#[cfg(target_os = "windows")]
fn main() {
    println!("cargo:rustc-link-lib=legacy_stdio_definitions");
}
#[cfg(not(target_os = "windows"))]
fn main() {
}