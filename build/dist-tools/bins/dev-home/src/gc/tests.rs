use std::fs;
use std::time::Duration;

use testkit::write_file;

use crate::gc::{Policy, STAMP_FILE, collect, is_due};
use crate::generation::{GENERATIONS_DIR, Materialized, TARGETS_DIR, USED_FILE, target_marker};
use crate::lock::StoreLock;
use crate::store::STORE_DIR;
use crate::test_support::{Fixture, age, names};

const DAY: Duration = Duration::from_hours(24);

/// Ages a generation: its last use was `days` ago, for every target.
fn age_generation(home: &Materialized, days: u64) {
    age(&home.home.join(USED_FILE), DAY * u32::try_from(days).unwrap());
    let marker = home.home.join(TARGETS_DIR).join(target_marker("//build:idea"));
    age(&marker, DAY * u32::try_from(days).unwrap());
}

/// Builds the generations `v5:g0` to `v5:g<count - 1>`, each with another `lib/app.jar`.
fn generations(fixture: &Fixture, count: usize) -> Vec<Materialized> {
    (0..count)
        .map(|index| {
            fixture.rebuild_source("lib/app.jar", &format!("app {index}"));
            fixture.write_layout();
            fixture.set_fingerprint(&format!("v5:g{index}"));
            fixture.materialize().unwrap()
        })
        .collect()
}

#[test]
fn a_collection_deletes_an_old_generation_and_its_blobs() {
    let fixture = Fixture::new("");
    let homes = generations(&fixture, 4);
    // The oldest was used 9 days ago, the other three 8, 2 and 1 days ago. The three newest of the target stay.
    for (home, days) in homes.iter().zip([9, 8, 2, 1]) {
        age_generation(home, days);
    }
    let blobs = names(&fixture.root().join(STORE_DIR)).len();
    let report = collect(fixture.root(), &Policy::DEFAULT).unwrap();
    assert_eq!(report.deleted_generations, ["v5-g0-flat"]);
    assert!(!report.busy);
    assert_eq!(
        names(&fixture.root().join(GENERATIONS_DIR)),
        ["v5-g1-flat", "v5-g2-flat", "v5-g3-flat"]
    );
    // Only `app 0` and the fingerprint `v5:g0` were in that generation alone.
    assert_eq!(report.deleted_blobs, 2);
    assert_eq!(names(&fixture.root().join(STORE_DIR)).len(), blobs - 2);
    assert!(fixture.root().join(STAMP_FILE).is_file());
}

#[test]
fn a_generation_used_this_week_stays() {
    let fixture = Fixture::new("");
    let homes = generations(&fixture, 5);
    for (home, days) in homes.iter().zip([6, 5, 4, 3, 2]) {
        age_generation(home, days);
    }
    let report = collect(fixture.root(), &Policy::DEFAULT).unwrap();
    assert_eq!(report.deleted_generations, Vec::<String>::new());
    assert_eq!(names(&fixture.root().join(GENERATIONS_DIR)).len(), 5);
}

#[test]
fn a_materialize_and_then_its_collection_delete_an_old_generation() {
    let fixture = Fixture::new("");
    let homes = generations(&fixture, 4);
    age_generation(&homes[0], 30);
    // The start released its shared lock, so the collection gets the exclusive one at once.
    let policy = Policy {
        lock_wait: Duration::ZERO,
        ..Policy::DEFAULT
    };
    let report = collect(fixture.root(), &policy).unwrap();
    assert_eq!(report.deleted_generations, ["v5-g0-flat"]);
}

#[test]
fn a_collection_gives_up_while_a_materialize_holds_the_lock() {
    let fixture = Fixture::new("");
    let homes = generations(&fixture, 4);
    age_generation(&homes[0], 30);
    let lock = StoreLock::shared(fixture.root()).unwrap();
    let policy = Policy {
        lock_wait: Duration::from_millis(100),
        ..Policy::DEFAULT
    };
    let report = collect(fixture.root(), &policy).unwrap();
    drop(lock);
    assert!(report.busy);
    assert!(homes[0].home.exists());
}

#[test]
fn the_trash_of_an_interrupted_collection_goes() {
    let fixture = Fixture::new("");
    fixture.materialize().unwrap();
    let trash = fixture.root().join(GENERATIONS_DIR).join(".trash-1-1");
    write_file(trash.join("lib/app.jar"), "app");
    collect(fixture.root(), &Policy::DEFAULT).unwrap();
    assert!(!trash.exists());
}

#[test]
fn an_old_staging_directory_goes_and_a_new_one_stays() {
    let fixture = Fixture::new("");
    fixture.materialize().unwrap();
    let generations = fixture.root().join(GENERATIONS_DIR);
    let old = generations.join("v5-x-flat.tmp-1-1");
    let new = generations.join("v5-y-flat.tmp-2-2");
    fs::create_dir_all(&old).unwrap();
    fs::create_dir_all(&new).unwrap();
    let policy = Policy {
        temporary_age: Duration::from_secs(60),
        ..Policy::DEFAULT
    };
    let stamp = old.join("file");
    write_file(&stamp, "");
    // A directory takes the time of its last entry change, so age it after the write.
    filetime_directory(&old, Duration::from_secs(120));
    collect(fixture.root(), &policy).unwrap();
    assert!(!old.exists());
    assert!(new.exists());
}

fn filetime_directory(directory: &std::path::Path, age: Duration) {
    let mut options = fs::OpenOptions::new();
    #[cfg(windows)]
    {
        use std::os::windows::fs::OpenOptionsExt as _;
        // Windows opens a directory only with backup semantics, and a new time needs the right to write attributes.
        const FILE_WRITE_ATTRIBUTES: u32 = 0x100;
        const FILE_FLAG_BACKUP_SEMANTICS: u32 = 0x0200_0000;
        options.access_mode(FILE_WRITE_ATTRIBUTES).custom_flags(FILE_FLAG_BACKUP_SEMANTICS);
    }
    #[cfg(not(windows))]
    options.read(true);
    let handle = options.open(directory).unwrap();
    handle.set_modified(std::time::SystemTime::now() - age).unwrap();
}

#[test]
fn a_collection_is_due_after_a_new_generation_or_a_day() {
    let fixture = Fixture::new("");
    fs::create_dir_all(fixture.root()).unwrap();
    assert!(is_due(fixture.root(), false));
    write_file(fixture.root().join(STAMP_FILE), "");
    assert!(!is_due(fixture.root(), false));
    assert!(is_due(fixture.root(), true));
    age(&fixture.root().join(STAMP_FILE), DAY * 2);
    assert!(is_due(fixture.root(), false));
}
