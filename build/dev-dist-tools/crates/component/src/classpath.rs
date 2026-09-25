//! The order and the text of `core-classpath.txt`.

/// The jars that start the core classpath, in this order.
const LEADING_JARS: [&str; 4] = [
    "lib/platform-loader.jar",
    "lib/util-8.jar",
    "lib/util.jar",
    "lib/product-backend.jar",
];

/// Applies the order of the Kotlin `generateClassPathByLayoutReport` (`classpath.kt`) to home-relative entries. The
/// leading jars come first in a fixed order. The other entries follow in bytewise order. Each leading jar moves once,
/// so a repeated entry keeps its later copies in the rest.
///
/// Each core classpath jar is a component file, so [`filemeta::merge`] refuses a name that is not ASCII or has an empty
/// segment. For such a name, the bytewise order is the order of Java `Path` on Unix and of Java `String` on Windows.
pub fn order_core_classpath_entries<S: AsRef<str>>(entries: &[S]) -> Vec<String> {
    let mut remaining: Vec<String> = entries.iter().map(|entry| entry.as_ref().to_owned()).collect();
    let mut result = Vec::with_capacity(remaining.len());
    for jar in LEADING_JARS {
        if let Some(index) = remaining.iter().position(|entry| entry == jar) {
            result.push(remaining.remove(index));
        }
    }
    remaining.sort();
    result.extend(remaining);
    result
}

/// The text of `core-classpath.txt`: the entries joined by `\n`, without a trailing newline.
pub fn core_classpath_text<S: AsRef<str>>(entries: &[S]) -> String {
    entries.iter().map(AsRef::as_ref).collect::<Vec<_>>().join("\n")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn leading_jars_come_first() {
        let entries = ["lib/app-backend.jar", "lib/util.jar", "lib/platform-loader.jar", "lib/util-8.jar"];
        assert_eq!(
            order_core_classpath_entries(&entries),
            ["lib/platform-loader.jar", "lib/util-8.jar", "lib/util.jar", "lib/app-backend.jar"]
        );
    }

    #[test]
    fn components_are_ordered_together() {
        let entries = ["lib/platform.jar", "plugins/sample/lib/sample.jar", "plugins/extra/lib/extra.jar"];
        assert_eq!(
            order_core_classpath_entries(&entries),
            ["lib/platform.jar", "plugins/extra/lib/extra.jar", "plugins/sample/lib/sample.jar"]
        );
    }

    #[test]
    fn the_text_has_no_trailing_newline() {
        assert_eq!(core_classpath_text(&["lib/util.jar", "lib/app.jar"]), "lib/util.jar\nlib/app.jar");
        assert_eq!(core_classpath_text::<&str>(&[]), "");
    }
}
