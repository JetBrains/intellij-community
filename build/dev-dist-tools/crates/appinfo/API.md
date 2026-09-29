# appinfo

Reads, merges and writes the application info of a product. The descriptor writer and the product files tool use it.

## Dependency

```toml
appinfo.workspace = true
```

## API

```rust
pub mod descriptorxml; // the element tree, `read` and `write`, byte for byte as `JDOMUtil.load` and `JDOMUtil.write`

pub const APPLICATION_INFO_NAMESPACE: &str;

pub struct Replacement { pub key: String, pub value: String } // Clone + Debug + PartialEq + Eq
impl Replacement { pub fn new(key: &str, value: &str) -> Replacement; }
pub fn replace_markers(text: &str, replacements: &[Replacement]) -> String;

pub struct ApplicationInfoElements { pub root: Element, pub names: usize, pub version: usize, pub build: usize }
impl ApplicationInfoElements {
    pub fn parse(content: &str, file: &str) -> anyhow::Result<ApplicationInfoElements>;
    pub fn element(&self, index: usize) -> &Element;
    pub fn element_mut(&mut self, index: usize) -> &mut Element;
}
pub fn merge_host_application_info(client: &mut ApplicationInfoElements, host: &ApplicationInfoElements, host_file: &str) -> anyhow::Result<()>;
pub fn copy_application_info_attribute(target: &mut Element, source: &Element, name: &str);

pub struct ApplicationInfo { // Clone + Debug + PartialEq + Eq
    pub full_product_name: String,
    pub edition: Option<String>,
    pub version: String,
    pub version_suffix: Option<String>,
    pub is_eap: bool,
    pub svg_icon: Option<String>,
    pub short_company_name: String,
    pub major_release_date: String,
}
impl ApplicationInfo {
    pub fn read(content: &str, file: &str, pinned_build_date_seconds: i64) -> anyhow::Result<ApplicationInfo>;
    pub fn read_frontend(content: &str, file: &str, host_content: &str, host_file: &str, pinned_build_date_seconds: i64) -> anyhow::Result<ApplicationInfo>;
    pub fn product_name_with_edition(&self) -> String;
}
pub fn format_version(pattern: &str, parts: [&str; 4]) -> anyhow::Result<String>;
pub fn format_major_release_date(raw: Option<&str>, build_date_seconds: i64) -> anyhow::Result<String>;
pub fn linux_frame_class(product_name_with_edition: &str) -> String;
pub fn shorten_company_name(name: &str) -> &str;
```

- `descriptorxml::read` lists the XML constructs that it refuses.
- `replace_markers` replaces each `__KEY__` in order. A marker without a replacement stays in the text.
- `ApplicationInfoElements::parse` refuses a document without exactly one `names`, `version` and `build` element in
  the application-info namespace.
- `merge_host_application_info` is the XML of the frontend override. `ApplicationInfo::read_frontend` reads the facts
  of that override: a value of the host takes precedence, and a value that the host does not state falls back to the
  frontend.
- `ApplicationInfo::read` finds an element and an attribute by the local name, as `readXmlAsModel` does. It refuses
  an element with two attributes of one local name.
- `ApplicationInfo::read` refuses a release product without a `majorReleaseDate`, and an application info without
  `version`, `names`, `company`, a `major` version, a `product` name or a company `name`.
- `format_version` accepts only literal text and the elements `{0}` to `{3}`. It refuses a quote and every other
  `{` or `}`.
- `format_major_release_date` accepts `yyyyMMdd` and `yyyyMMddHHmm` of a valid date and time. No value or a `__`
  marker gives the build date in UTC.

## Kotlin mapping

| Kotlin | Rust |
|---|---|
| `ApplicationInfoPropertiesImpl` of `ApplicationInfoPropertiesImpl.kt` | `ApplicationInfo::read` |
| `JetBrainsClientPropertiesForLaunchers.applicationInfoOverride` | `ApplicationInfo::read_frontend`, `merge_host_application_info` |
| `BuildUtils.replaceAll(text, map, "__")` | `replace_markers` |
| `formatMajorReleaseDate` | `format_major_release_date` |
| `linuxFrameClass` of `BuildTasksImpl.kt` | `linux_frame_class` |
| `shortenCompanyName` | `shorten_company_name` |
