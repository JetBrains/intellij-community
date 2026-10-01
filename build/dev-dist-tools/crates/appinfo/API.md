# appinfo

Reads, merges and writes the application info of a product, and holds the one descriptor XML reader and writer. The
descriptor writer and the product files tool use the application info. The descriptor writer reads the facts for the
stamps of a plugin descriptor too. The runtime layout tool and `dev-dist` read descriptors through
`descriptorxml::read`.

## Dependency

```toml
appinfo.workspace = true
```

## API

```rust
pub mod descriptorxml; // the element tree, `read` and `write`, byte for byte as `JDOMUtil.load` and `JDOMUtil.write`

pub const APPLICATION_INFO_NAMESPACE: &str;

pub struct Replacement { pub key: String, pub value: String } // Clone + Debug + PartialEq + Eq
impl Replacement {
    pub fn new(key: &str, value: &str) -> Replacement;
    pub fn parse(value: &str) -> anyhow::Result<Replacement>;
    pub fn parse_all(values: &[String]) -> anyhow::Result<Vec<Replacement>>;
}
pub fn replace_markers(text: &str, replacements: &[Replacement]) -> String;

pub struct ApplicationInfoElements { /* private */ }
impl ApplicationInfoElements {
    pub fn parse(content: &str, file: &Path) -> anyhow::Result<ApplicationInfoElements>;
    pub fn root(&self) -> &Element;
    pub fn names(&self) -> &Element;      // and names_mut
    pub fn version(&self) -> &Element;    // and version_mut
    pub fn build(&self) -> &Element;      // and build_mut
}
pub fn merge_host_application_info(client: &mut ApplicationInfoElements, host: &ApplicationInfoElements, host_file: &Path) -> anyhow::Result<()>;

pub struct ApplicationInfo { // Clone + Debug + PartialEq + Eq
    pub full_product_name: String,
    pub edition: Option<String>,
    pub version: String,
    pub major_version: String,
    pub minor_version_main_part: String,
    pub version_suffix: Option<String>,
    pub is_eap: bool,
    pub svg_icon: Option<String>,
    pub short_company_name: String,
    pub major_release_date: String,
}
impl ApplicationInfo {
    pub fn load(path: &Path, replacements: &[Replacement], host: Option<&Path>, pinned_build_date_seconds: i64) -> anyhow::Result<ApplicationInfo>;
    pub fn release_version_for_licensing(&self) -> String;
    pub fn product_name_with_edition(&self) -> String;
}
pub fn format_version(pattern: &str, parts: [&str; 4]) -> anyhow::Result<String>;
pub fn format_major_release_date(raw: Option<&str>, build_date_seconds: i64) -> anyhow::Result<String>;
pub fn linux_frame_class(product_name_with_edition: &str) -> String;
pub fn shorten_company_name(name: &str) -> &str;
```

- `descriptorxml::read` lists the XML constructs that it refuses.
- `Replacement::parse` reads one `KEY=VALUE` of a `--replacement` option. The value keeps every `=` after the first
  one and can be empty. It refuses a value without `=` or with an empty key: `a replacement is '<key>=<value>', and
  "VALUE" is not`. `Replacement::parse_all` reads the values of every option in order and refuses a key that occurs
  twice: `the replacement "A" is stated more than once`. The descriptor writer and the product files tool both read
  their options with it.
- `replace_markers` replaces each `__KEY__` in order. A marker without a replacement stays in the text.
- `ApplicationInfoElements::parse` refuses a document without exactly one `names`, `version` and `build` element in
  the application-info namespace. The positions of the three elements are private, so an accessor always gives the
  element that the parse found.
- `ApplicationInfo::load` is the one reader of the facts. It reads the file, replaces the markers, then reads the XML.
  An I/O error reads `cannot read <path>: <io::Error>`. With `host`, it reads the facts of the frontend override,
  whose XML `merge_host_application_info` writes: a value of the host takes precedence, and a value that the host does
  not state falls back to the frontend. The host text gets no marker replacement, as the override reads the raw file.
- `ApplicationInfo::load` finds an element and an attribute by the local name, as `readXmlAsModel` does. It refuses
  an element with two attributes of one local name.
- `ApplicationInfo::load` refuses a release product without a `majorReleaseDate`, and an application info without
  `version`, `names`, `company`, a `major` version, a `product` name or a company `name`.
- `format_version` accepts only literal text and the elements `{0}` to `{3}`. It refuses a quote and every other
  `{` or `}`.
- `format_major_release_date` accepts `yyyyMMdd` and `yyyyMMddHHmm` of a valid date and time. No value or a `__`
  marker gives the build date in UTC.

## Kotlin mapping

| Kotlin | Rust |
|---|---|
| `ApplicationInfoPropertiesImpl` of `ApplicationInfoPropertiesImpl.kt` | `ApplicationInfo::load` |
| `ApplicationInfoProperties.releaseVersionForLicensing` | `ApplicationInfo::release_version_for_licensing` |
| `JetBrainsClientPropertiesForLaunchers.applicationInfoOverride` | `ApplicationInfo::load` with a host, `merge_host_application_info` |
| `BuildUtils.replaceAll(text, map, "__")` | `replace_markers` |
| `formatMajorReleaseDate` | `format_major_release_date` |
| `linuxFrameClass` of `BuildTasksImpl.kt` | `linux_frame_class` |
| `shortenCompanyName` | `shorten_company_name` |
