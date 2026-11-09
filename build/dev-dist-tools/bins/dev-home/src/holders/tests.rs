use crate::holders::is_held;
use crate::test_support::Fixture;

#[test]
fn a_generation_that_no_process_holds_is_free() {
    let fixture = Fixture::new("");
    let home = fixture.materialize().unwrap();
    assert!(!is_held(&home.home));
}

#[cfg(unix)]
#[test]
fn a_generation_with_an_open_jar_is_held() {
    let fixture = Fixture::new("");
    let home = fixture.materialize().unwrap();
    let jar = std::fs::File::open(home.home.join("lib/app.jar")).unwrap();
    assert!(is_held(&home.home));
    drop(jar);
}

#[cfg(unix)]
#[test]
fn a_contents_home_with_an_open_jar_is_held() {
    let fixture = Fixture::new("Contents");
    let home = fixture.materialize().unwrap();
    let generation = home.home.parent().unwrap();
    let jar = std::fs::File::open(home.home.join("lib/util.jar")).unwrap();
    assert!(is_held(generation));
    drop(jar);
}
