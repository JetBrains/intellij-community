use std::fs;

use serde::Deserialize;

use super::*;

/// One case of `testdata/mac-binary-vectors.json`. `MacSigningInputsTest` of the production build checks the same names.
#[derive(Deserialize)]
struct Vector {
    name: String,
    bytes: String,
    kind: Option<MacBinary>,
}

#[derive(Deserialize)]
struct Vectors {
    vectors: Vec<Vector>,
}

fn decode_hex(text: &str) -> Vec<u8> {
    assert!(text.len().is_multiple_of(2), "odd hex length");
    (0..text.len())
        .step_by(2)
        .map(|index| u8::from_str_radix(&text[index..index + 2], 16).unwrap())
        .collect()
}

fn vectors() -> Vec<Vector> {
    let path = testkit::testdata("mac-binary-vectors.json");
    let data = fs::read(&path).unwrap_or_else(|error| panic!("{}: {error}", path.display()));
    serde_json::from_slice::<Vectors>(&data).unwrap().vectors
}

#[test]
fn each_vector_has_its_kind_and_the_hash_of_xxh3() {
    let temporary = tempfile::tempdir().unwrap();
    let vectors = vectors();
    assert_eq!(vectors.len(), 14);
    for vector in vectors {
        let file = temporary.path().join("file");
        fs::write(&file, decode_hex(&vector.bytes)).unwrap();
        assert_eq!(detect(&file).unwrap(), vector.kind, "{}", vector.name);
        let (hash, kind) = hash_file(&file).unwrap();
        assert_eq!(kind, vector.kind, "{}", vector.name);
        assert_eq!(hash, xxh3::hash_file(&file).unwrap(), "{}", vector.name);
    }
}

#[test]
fn the_kind_has_the_json_text_of_the_manifest() {
    let text = serde_json::to_string(&[MacBinary::Executable, MacBinary::Dylib, MacBinary::Bundle]).unwrap();
    assert_eq!(text, r#"["executable","dylib","bundle"]"#);
}

#[test]
fn a_missing_file_keeps_the_error_kind() {
    let temporary = tempfile::tempdir().unwrap();
    let error = hash_file(&temporary.path().join("missing")).unwrap_err();
    assert_eq!(error.kind(), io::ErrorKind::NotFound);
}
