# Content Module Embedding: Scrambling-Aware Architecture

## Overview

This document explains how the IntelliJ Platform build system handles content module descriptor embedding in a scrambling-aware manner, solving two critical issues with obfuscated code.

**Related Issue**: IJPL-215077

**Related Files**:
- `classPath/contentModuleEmbedding.kt` - Core embedding functions
- `classPath/classpath.kt` - plugin-classpath.txt generation & scrambled embedding
- `impl/productModuleLayout.kt` - Product module handling
- `impl/PluginXmlPatcher.kt` - Plugin XML patching & non-scrambled embedding
- `impl/CachedDescriptorContainer.kt` - Descriptor cache implementation
- `zkm/ZkmScrambleTool.kt` - Scrambling & cache updates
- Runtime: `core-impl/src/com/intellij/ide/plugins/PluginDescriptorLoader.kt`

## Content Modules: Product vs Plugin

### What are Content Modules?

**Content modules** are a modular way to organize plugin/product code. They allow:
- Conditional loading of features
- Better code organization
- Separate classloaders for different parts

```xml
<content>
  <module name="my.module.core"/>
  <module name="my.module.optional" loading="on-demand"/>
</content>
```

### Product Modules vs Plugin Content Modules

| Aspect | **Product Modules** | **Plugin Content Modules** |
|--------|-------------------|---------------------------|
| **Definition** | Content modules of the core product/platform | Content modules within individual plugins |
| **Descriptor** | Main product plugin.xml (e.g., `IdeaCorePlugin.xml`) | Plugin's plugin.xml file |
| **Layout Type** | `PlatformLayout` | `PluginLayout` |
| **Handled In** | `productModuleLayout.kt::processProductModule()` | `PluginXmlPatcher.kt::patchPluginXml()` |
| **Examples** | `intellij.platform.coverage`, `intellij.platform.debugger.impl` | Plugin-specific modules |
| **Scrambling Check** | `isInScrambledFile` (embedded + closed-source) | `pathsToScramble.isEmpty()` |

**Key Insight**: Product modules are essentially "content modules of the core product" and follow the same XML structure but with different embedding logic.

## The Two Problems

### Problem 1: Scrambling Doesn't Modify Class Names in CDATA

When content module descriptors were embedded as `CDATA` sections in XML at build time, the scrambler (obfuscator) couldn't modify class names inside those CDATA sections because XML scramblers don't process CDATA content.

```xml
<!-- Scrambler CANNOT modify class names here ❌ -->
<module name="some.module"><![CDATA[
  <extensions>
    <implementation class="com.example.OriginalClassName"/>
  </extensions>
]]></module>
```

### Problem 2: Files Modified by Scrambling Were Not Respected

Previously, descriptor content was inlined too early in the build process (before scrambling), so scrambled class names weren't reflected in the final descriptors.

```
Old Flow:
  Compilation → Layout (inline descriptors) → Scrambling → Distribution
                           ↑
                  Problem: Uses unscrambled names!
```

## Technical Constraint: Why CDATA Sections Are Opaque

### XML CDATA Fundamentals

CDATA (Character Data) sections tell XML parsers: "treat this as raw text, don't parse it."

**Example**:
```xml
<module name="my.module"><![CDATA[
  <extensions>
    <implementation class="com.example.OriginalClassName"/>
  </extensions>
]]></module>
```

Inside the CDATA:
- `<extensions>` is NOT parsed as an XML element (it's text)
- `class="com.example.OriginalClassName"` is NOT parsed as an attribute (it's text)
- The entire content is a single text node

### Why Scrambling Can't Modify CDATA

Scrambling tools that work on XML typically:
1. Parse XML into a DOM or SAX event stream
2. Navigate the element tree
3. Find `class="..."` attributes
4. Modify class names in-place
5. Serialize back to XML

**Problem**: Inside CDATA, there IS no element tree. It's all text.

```
Regular XML:                    CDATA:
<class>Foo</class>             <![CDATA[<class>Foo</class>]]>
     ↓                                      ↓
Element with text content       Single text node: "<class>Foo</class>"
(scrambler can parse)           (scrambler sees raw string)
```

### Why We Use CDATA for Embedding

Content module descriptors must be embedded as CDATA because:
- They contain arbitrary XML (plugin descriptor format)
- They may have different namespace declarations
- They need to be treated as opaque by the parent descriptor parser
- Runtime can parse them separately with proper context

**The Catch-22**:
- ✓ Must use CDATA to embed descriptors properly
- ✗ CDATA content can't be scrambled
- ✓ Solution: Embed AFTER scrambling (descriptors already have scrambled names)

## The Descriptor Cache: Bridge Between Build Phases

### What is CachedDescriptorContainer?

`CachedDescriptorContainer` is a thread-safe, in-memory cache that stores plugin descriptor file contents (XML) as byte arrays throughout the build process. It acts as a shared state container that spans multiple build phases.

**Key Characteristics**:
- Scoped by target (platform vs plugin directory)
- Concurrent map: `ConcurrentHashMap<Key, Map<String, ByteArray>>`
- Lives from Layout phase through Distribution phase
- Updated in-place by scrambling process

### Cache Lifecycle

```
┌─────────────────────────────────────────────────────┐
│           DESCRIPTOR CACHE LIFECYCLE                 │
└─────────────────────────────────────────────────────┘

LAYOUT PHASE (Populate Cache):
  ├─ JAR Packaging (JarPackager.kt)
  │  └─ Caches META-INF/*.xml from source JARs
  ├─ XML Patching (PluginXmlPatcher.kt)
  │  └─ Caches plugin.xml after patching
  └─ xi:include Resolution (XIncludeElementResolverImpl)
     └─ Caches included descriptor files

SCRAMBLING PHASE (Update Cache):
  └─ ZkmScrambleTool.scramble()
     └─ updatePackageIndexUsingTempFile()
        └─ Reads scrambled JARs
        └─ Extracts META-INF/*.xml files
        └─ Updates cache with scrambled content
           (Class names now obfuscated!)

CLASSPATH GENERATION (Read Cache):
  └─ classpath.kt::generatePluginClassPath()
     └─ Reads from cache (now contains scrambled names)
     └─ Embeds in plugin-classpath.txt
```

### Why the Cache is Critical

Without the cache, the build system would read descriptors from JARs multiple times:
1. **During layout**: Read original descriptors
2. **After scrambling**: JARs are modified, descriptors contain scrambled names
3. **During embedding**: Would re-read from disk (wrong timing!)

The cache solves this by:
- Storing descriptors in memory (no re-parsing)
- Being explicitly updated by scrambling (correct timing)
- Providing a single source of truth for all consumers

### Cache Update During Scrambling

**Location**: `ZkmScrambleTool.kt::updatePackageIndexUsingTempFile()` (lines 513-535)

After scrambling modifies JAR files, this function:
1. Reads the scrambled JAR
2. Extracts all XML files from `META-INF/`
3. Updates the cache with scrambled content
4. Applies changes atomically

```kotlin
// Simplified code from ZkmScrambleTool.kt
readZipFile(originalFile) { name, data ->
  if ((name.startsWith("META-INF/") || !name.contains('/')) && name.endsWith(".xml")) {
    cacheWriter.put(name, dataBuffer.toByteArray())
  }
}
cacheWriter.apply()  // Atomic update
```

**Critical**: This is why `embedContentModules()` in `classpath.kt` must run AFTER scrambling - it reads from this updated cache.

### Cache Scoping

The cache is scoped to prevent conflicts:

```kotlin
// Platform scope - all platform modules
val platformCache = cachedDescriptorContainer.forPlatform(platformLayout)

// Plugin scope - specific plugin directory (includes OS variant)
val pluginCache = cachedDescriptorContainer.forPlugin(pluginDir)
```

This ensures:
- Platform descriptors don't conflict with plugin descriptors
- OS-specific plugin variants have separate caches
- Multiple plugins can build concurrently

## The Solution: Dual-Path Conditional Embedding

The solution uses **two different code paths** depending on whether code is scrambled:

### Path 1: Non-Scrambled Code (PluginXmlPatcher.kt)
- Embeds content modules into the **plugin.xml file** during XML patching
- Happens **before** plugin-classpath.txt generation
- Used for plugins with `pathsToScramble.isEmpty() == true`

### Path 2: Scrambled Code (classpath.kt)
- Embeds content modules into **plugin-classpath.txt** binary file
- Happens **after** scrambling, during classpath generation
- Reads from post-scrambling cache
- Used for plugins with `pathsToScramble.isEmpty() == false`

## xi:include Resolution: Two Levels

**Important**: There are two distinct levels of xi:include elements that are handled differently:

### Level 1: Structural xi:includes (Plugin Descriptor Structure)

**Always resolved** - These are xi:includes in the main plugin.xml that define the plugin structure.

```xml
<!-- These ARE resolved to find all <content> declarations -->
<idea-plugin>
  <xi:include href="META-INF/PlatformExtensionPoints.xml"/>
  <xi:include href="META-INF/SomeFileWith ContentTag.xml"/>
</idea-plugin>
```

**Why resolved?**
From `productModuleLayout.kt`:
```kotlin
// Scrambling isn't an issue: the scrambler can modify XML.
// If a file is included, we assume—and it should be the case—that both the including
// module and the module containing the included file are scrambled together.
// Note: CDATA isn't processed, so embedded content modules use different logic.
// We must resolve includes to collect all content modules, since the <content> tag may
// be specified in an included file. This is done not only for performance but for correctness.
```

**Key point**: We **must** resolve these xi:includes because:
- `<content>` tags might be in included files
- We need to find all content modules to process them
- Scrambling can modify these XML files (outside CDATA)

### Level 2: Content Module Descriptors (Inside `<content><module>`)

**Conditionally embedded** - These are the actual content module descriptor files.

```xml
<content>
  <!-- This module's descriptor is conditionally embedded as CDATA -->
  <module name="my.module.core"/>
</content>
```

**Handling depends on scrambling:**
- **Scrambled plugins**: Don't embed → defer to plugin-classpath.txt (after scrambling)
- **Non-scrambled plugins**: Embed now as CDATA with xi:includes resolved

**Why the distinction?**
- **Scrambled**: Embedding before scrambling would capture unscrambled class names in CDATA
- **Non-scrambled**: Can safely embed immediately; xi:includes need resolution for separate classloader

### Build Pipeline Flow (with Cache State)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                 COMPLETE BUILD PIPELINE WITH CACHE STATE                │
└─────────────────────────────────────────────────────────────────────────┘

  ┌──────────────┐
  │ Compilation  │  Compile source code to classes
  │    Phase     │  Cache State: Empty
  └──────┬───────┘
         │
         v
  ┌──────────────┐
  │    Layout    │  Organize modules into JARs
  │    Phase     │  • Create CachedDescriptorContainer
  │              │    - forPlatform() for platform descriptors
  │              │    - forPlugin() for plugin descriptors
  │              │  • Cache descriptors DURING:
  │              │    - JAR packaging (JarPackager.kt)
  │              │    - Plugin XML patching (PluginXmlPatcher.kt)
  │              │    - xi:include resolution (XIncludeElementResolverImpl)
  │              │  Cache State: Original descriptors ✓
  └──────┬───────┘
         │
         v
  ┌──────────────┐
  │ Scrambling   │  Obfuscate class names (if configured)
  │    Phase     │  • Modify JAR files
  │              │  • Update cache via updatePackageIndexUsingTempFile()
  │              │  • Read scrambled JARs, extract XML, update cache
  │              │  Cache State: Scrambled descriptors ✓
  └──────┬───────┘
         │
         ├─────────────────────────┬─────────────────────────────────┐
         │                         │                                 │
         v                         v                                 v
  ┌────────────────┐   ┌──────────────────┐        ┌──────────────────────┐
  │ PRODUCT        │   │ PLUGIN           │        │ PLUGIN               │
  │ MODULES        │   │ (Non-Scrambled)  │        │ (Scrambled)          │
  │                │   │                  │        │                      │
  │ Check:         │   │ PluginXmlPatcher │        │ Resolve structural   │
  │ isInScrambled  │   │ .kt (Layout)     │        │ xi:includes          │
  │ File?          │   │                  │        │ but DON'T embed      │
  │                │   │ if (pathsTo      │        │ content modules      │
  │ if (!isIn      │   │   Scramble       │        │                      │
  │   Scrambled    │   │   .isEmpty()) {  │        │ if (!pathsTo         │
  │   File) {      │   │   embed now      │        │   Scramble           │
  │   embed now    │   │   (reads cache:  │        │   .isEmpty()) {      │
  │   (reads cache:│   │   original)      │        │   defer to           │
  │   original)    │   │ }                │        │   classpath.kt       │
  │   (non-        │   │                  │        │ }                    │
  │   embedded:    │   │ Embeds content   │        │                      │
  │   separate     │   │ modules in       │        │                      │
  │   classloader) │   │ plugin.xml       │        │                      │
  └────────────────┘   └──────────────────┘        └──────────────────────┘
         │                         │                           │
         │                         │                           │
         v                         v                           v
  ┌──────────────────────────────────────────────────────────────┐
  │         Generate plugin-classpath.txt (classpath.kt)          │
  │                                                               │
  │  For scrambled plugins:                                       │
  │    if (!pluginLayout.pathsToScramble.isEmpty()) {            │
  │      embedContentModules(...)  // Read from UPDATED cache!   │
  │    }                           // Cache contains scrambled    │
  │                                // class names after Phase 3   │
  │  Result: Binary file with pre-computed metadata              │
  └──────────────────────────────────────────────────────────────┘
         │
         v
  ┌──────────────┐
  │Distribution  │  Generate OS-specific distributions
  │    Phase     │  Cache State: No longer needed
  └──────────────┘
```

## Embedding Logic: Complete Matrix

| Type | Scrambling Check | Condition | Action | Location |
|------|-----------------|-----------|--------|----------|
| **Product Module** | `isInScrambledFile`<br/>(embedded + closed-source) | `!isInScrambledFile` | **Embed now** in product XML<br/>(for xi:include resolution) | productModuleLayout.kt |
| **Product Module** | `isInScrambledFile` | `isInScrambledFile` | **Don't embed**<br/>(use plugin-classpath.txt) | productModuleLayout.kt |
| **Plugin Content** | `pathsToScramble.isEmpty()` | `.isEmpty()` | **Embed now** in plugin.xml | PluginXmlPatcher.kt |
| **Plugin Content** | `pathsToScramble.isEmpty()` | `!.isEmpty()` | **Defer to plugin-classpath.txt** | classpath.kt |

### Key Comment from productModuleLayout.kt

```kotlin
// We do not embed the module descriptor because scrambling can rename classes.
//
// However, we cannot rely solely on the `PLUGIN_CLASSPATH` descriptor: for non-embedded modules,
// xi:included files (e.g., META-INF/VcsExtensionPoints.xml) are not resolvable from the core
// classpath, since a non-embedded module uses a separate classloader.
//
// Because scrambling applies only (by policy) to embedded modules, we embed the module descriptor
// for non-embedded modules to address this.
//
// Note: We could implement runtime loading via the module's classloader, but that would
// significantly complicate the runtime code.

if (!isInScrambledFile) {
  resolveAndEmbedContentModuleDescriptor(...)
}
```

**Why the distinction?**
- **Embedded + closed-source modules** will be scrambled → defer to plugin-classpath.txt
- **Non-embedded modules** need xi:includes resolved now → embed in product XML

## The plugin-classpath.txt Mechanism

### What is plugin-classpath.txt?

A **binary optimization file** containing pre-computed plugin metadata:
- Pre-sorted JAR paths
- Pre-parsed plugin descriptors (with embedded content modules for scrambled plugins)
- Eliminates disk I/O and JAR scanning at runtime

**Location**: `lib/plugin-classpath.txt` in the distribution

### Build-Time Generation (classpath.kt)

**Functions**:
- `writePluginClassPathHeader()` - Writes format version, product descriptor, plugin count
- `generatePluginClassPath()` - Writes plugin entries with descriptors and file lists

**Critical Code** (classpath.kt):
```kotlin
// ONLY embed if scrambling is enabled
if (!pluginLayout.pathsToScramble.isEmpty()) {
  val xIncludeResolver = createXIncludeElementResolver(
    searchPath = listOf(
      pluginLayout.includedModules.mapTo(LinkedHashSet()) { it.moduleName }
        to pluginDescriptorContainer,
      platformLayout.includedModules.mapTo(LinkedHashSet()) { it.moduleName }
        to platformDescriptorContainer,
    ),
    context = context,
  )

  embedContentModules(
    rootElement = rootElement,
    pluginLayout = pluginLayout,
    pluginDescriptorContainer = pluginDescriptorContainer,
    xIncludeResolver = xIncludeResolver,
    context = context,
  )
}
// else: Don't embed content modules; defer to plugin-classpath.txt
//       (structural xi:includes ARE still resolved to find <content> tags)
```

### Runtime Loading (PluginDescriptorLoader.kt)

**Process**:
1. Read `plugin-classpath.txt` (binary format)
2. Parse header (version, product descriptor, plugin count)
3. For each plugin entry:
   - Read file count, plugin dir, **descriptor bytes**
   - Create FileItems array
   - Parse descriptor from memory (no disk I/O!)
4. Handle content modules:
   - If `descriptorContent != null`: Parse embedded CDATA (scrambled names!)
   - If `descriptorContent == null`: Load from file/JAR

**Key Code** (PluginDescriptorLoader.kt):
```kotlin
for (module in descriptor.content.modules) {
  if (module.descriptorContent == null) {
    // Not embedded - load from separate file/JAR
    // This happens for non-scrambled plugins
    val jarFile = pluginDir.resolve("lib/modules/${module.moduleId.name}.jar")
    classPath = Collections.singletonList(jarFile)
    loadModuleFromSeparateJar(...)
  }
  else {
    // Embedded as CDATA - parse directly from memory!
    // This happens for scrambled plugins
    // Class names are already scrambled!
    val subRaw = PluginDescriptorFromXmlStreamConsumer(...).let {
      it.consume(createXmlStreamReader(module.descriptorContent))
      it.getBuilder()
    }
  }
}
```

### Binary File Format

```
┌─────────────────────────────────────────────────────────────┐
│                   plugin-classpath.txt                       │
├─────────────────────────────────────────────────────────────┤
│ HEADER                                                       │
├─────────────────────────────────────────────────────────────┤
│ [1 byte]  Format Version (2)                                │
│ [1 byte]  jarOnly Flag (0/1)                                │
│ [4 bytes] Product Descriptor Size                           │
│ [N bytes] Product Descriptor Content (XML with CDATA)       │
│ [2 bytes] Plugin Count                                      │
├─────────────────────────────────────────────────────────────┤
│ PLUGIN ENTRIES (repeated for each plugin)                   │
├─────────────────────────────────────────────────────────────┤
│ [2 bytes] File Count                                        │
│ [UTF]     Plugin Directory Name                             │
│ [4 bytes] Plugin Descriptor Size                            │
│ [N bytes] Plugin Descriptor Content (XML, possibly CDATA)   │
│ [UTF]     File Path 1 (relative to plugin dir)             │
│ [UTF]     File Path 2                                       │
│ ...                                                          │
│ [UTF]     File Path N                                       │
└─────────────────────────────────────────────────────────────┘
```

### Performance Benefits

| Without plugin-classpath.txt | With plugin-classpath.txt |
|------------------------------|---------------------------|
| Scan plugin directories | Read single binary file |
| Open each JAR to find plugin.xml | Descriptors already in memory |
| Parse XML for each plugin | Parse pre-cached bytes |
| Resolve xi:includes at runtime | Embedded as CDATA (if scrambled) |

## Complete Data Flow: Build to Runtime

```
┌─────────────────────────────────────────────────────────────────┐
│              BUILD TIME → RUNTIME CONNECTION                     │
└─────────────────────────────────────────────────────────────────┘

BUILD TIME                              RUNTIME
┌──────────────────────┐              ┌────────────────────────────┐
│ 1. Compilation       │              │ 1. Read plugin-classpath   │
│    └─> Source to     │              │    .txt                    │
│        classes       │              │                            │
├──────────────────────┤              ├────────────────────────────┤
│ 2. Layout            │              │ 2. Parse header            │
│    └─> Organize JARs │              │    • Version               │
│    └─> Cache         │              │    • Product descriptor    │
│        descriptors   │              │    • Plugin count          │
│        (during JAR   │              ├────────────────────────────┤
│        packaging,    │              │ 3. For each plugin:        │
│        XML patching) │              │    • Read descriptor bytes │
├──────────────────────┤              │    • Parse from memory     │
│ 3. Scrambling        │    writes    │      (no disk I/O!)        │
│    └─> Obfuscate     │  ═══════>    ├────────────────────────────┤
│        class names   │   to file    │ 4. Handle content modules: │
│    └─> Modifies      │              │    if (descriptorContent   │
│        cached        │              │        != null) {          │
│        descriptors   │              │      // Embedded CDATA     │
├──────────────────────┤              │      // Scrambled names!   │
│ 4a. PluginXmlPatcher │              │      parse from memory     │
│     if (pathsTo      │              │    } else {                │
│       Scramble       │              │      // Load from file     │
│       .isEmpty()) {  │              │      load via DataLoader   │
│       embed in       │              │    }                       │
│       plugin.xml     │              ├────────────────────────────┤
│     }                │              │ 5. Create classloaders     │
│                      │              │    with pre-sorted JARs    │
│ 4b. classpath.kt     │              └────────────────────────────┘
│     if (!pathsTo     │
│       Scramble       │
│       .isEmpty()) {  │
│       embed in       │
│       PLUGIN_        │
│       CLASSPATH.txt  │
│       (read from     │
│       cache)         │
│     }                │
└──────────────────────┘
         │
         └──── Content modules with scrambled class names ────────┘
```

## Key Functions

### `embedContentModules()`

**Location**: `classPath/contentModuleEmbedding.kt`

**Purpose**: Embeds content module descriptors as CDATA in a plugin's root descriptor.

**Called From**:
- `classpath.kt` - For scrambled plugins (plugin-classpath.txt generation)
- `PluginXmlPatcher.kt` - For non-scrambled plugins (plugin.xml patching)

**Process**:
1. Iterates through all `<content>/<module>` elements
2. For each module, calls `resolveAndEmbedContentModuleDescriptor()`
3. Applies optional modifications (e.g., `separate-jar` attribute)
4. Embeds resolved descriptor as CDATA

### `resolveAndEmbedContentModuleDescriptor()`

**Location**: `classPath/contentModuleEmbedding.kt`

**Purpose**: Helper function that resolves a content module descriptor and embeds it as CDATA.

**Key Features**:
- Checks if content is already embedded (idempotent)
- Resolves module descriptor from cache (post-scrambling if applicable)
- Applies optional descriptor modifications via callback
- Embeds result as CDATA in the module element

**Critical**: Reads from cache **after** scrambling, ensuring scrambled class names are used.

### `resolveContentModuleDescriptor()`

**Location**: `classPath/contentModuleEmbedding.kt`

**Purpose**: Resolves and loads a content module descriptor from cache or source.

**Cache Strategy**:
1. Check `cachedDescriptorContainer` for already-processed descriptor
2. If not cached, load from module sources and cache it
3. Resolve xi:include elements
4. Return processed Element

## Product Module Handling (PlatformLayout)

**Location**: `impl/productModuleLayout.kt::processProductModule()`

**Logic**:
```kotlin
val isEmbedded = moduleElement.getAttributeValue("loading") == "embedded"
val isInScrambledFile = isEmbedded && isModuleCloseSource(moduleName, context)

if (!isInScrambledFile) {
  // Non-scrambled OR non-embedded: Embed now for xi:include resolution
  resolveAndEmbedContentModuleDescriptor(
    moduleElement = moduleElement,
    cachedDescriptorContainer = cachedDescriptorContainer,
    xIncludeResolver = xIncludeResolver,
    context = context,
  )
}
// else: Scrambled file - will be handled via plugin-classpath.txt at runtimeƒ∂
```

**Why?**
- **Scrambled embedded modules**: Use plugin-classpath.txt (class names will be obfuscated)
- **Non-scrambled modules**: Embed now because separate classloader needs xi:includes resolved

## Plugin Content Module Handling (PluginLayout)

### Build Pipeline Context

PluginXmlPatcher runs during the **Layout Phase**, BEFORE scrambling occurs. At this point:
- Descriptors contain original (unscrambled) class names
- The cache contains original descriptors
- We must decide: embed now, or defer to post-scrambling?

**Note**: Before this logic runs, structural xi:includes in plugin.xml are ALREADY resolved to find all `<content>` tags.

### Path 1: PluginXmlPatcher.kt (Non-Scrambled Plugins)

**When**: Layout Phase (before scrambling)
**Location**: `impl/PluginXmlPatcher.kt::patchPluginXml()` (lines 94-108)
**Condition**: `pluginLayout.pathsToScramble.isEmpty() == true`

For non-scrambled plugins, it's safe to embed immediately:
- Class names won't change
- No scrambling phase will modify the JAR
- Embedding now provides runtime performance benefit

```kotlin
// Structural xi:includes already resolved by this point
filterAndProcessContentModules(rootElement, pluginMainModuleName, context) { moduleElement, moduleName, _ ->
  if (pluginLayout.pathsToScramble.isEmpty()) {
    // NOT scrambled → embed content module descriptors now in plugin.xml
    // Safe because: no scrambling = no class name changes
    embedContentModules(
      moduleElement = moduleElement,
      pluginDescriptorContainer = descriptorContainer,
      xIncludeResolver = xIncludeResolver,
      context = context,
      moduleName = moduleName,
    )
  }
  // else: Scrambling enabled → skip embedding
  //       Will be handled later in classpath.kt after scrambling
}
```

**What happens to scrambled plugins here?**
- Structural xi:includes ARE resolved (to find `<content>` tags)
- Content modules are NOT embedded (conditional skip via early return)
- The `<module>` elements remain empty
- Embedding deferred to Path 2 (after scrambling)

### Path 2: classpath.kt (Scrambled Plugins)

**When**: Classpath Generation Phase (after scrambling)
**Location**: `classPath/classpath.kt::generatePluginClassPath()` (lines 193-209)
**Condition**: `pluginLayout.pathsToScramble.isEmpty() == false`

For scrambled plugins, embedding must wait until after scrambling:
- Reads from cache (now contains scrambled class names)
- Embeds in plugin-classpath.txt binary file
- Runtime loads from memory (no disk I/O)

```kotlin
if (!pluginLayout.pathsToScramble.isEmpty()) {
  // IS scrambled → embed in plugin-classpath.txt
  // Reads from UPDATED cache (post-scrambling)
  embedContentModules(
    rootElement = rootElement,
    pluginLayout = pluginLayout,
    pluginDescriptorContainer = pluginDescriptorContainer,  // Contains scrambled content!
    xIncludeResolver = xIncludeResolver,
    context = context,
  )
}
```

**Key Insight**: The same `embedContentModules()` function is called in both paths, but:
- Path 1 reads from cache BEFORE scrambling (original names)
- Path 2 reads from cache AFTER scrambling (scrambled names)

## Before vs After: Product XML Files

### Before (Static Inlining)

```xml
<!-- CLionPlugin.xml: Example of problematic inlined content -->
<idea-plugin>
  <!-- <editor-fold desc="Inlined from PlatformLangPlugin.xml"> -->
  <id>com.intellij</id>
  <name>IDEA CORE</name>
  <module value="com.intellij.modules.platform" />
  <xi:include href="/META-INF/PlatformLangComponents.xml" />
  <!-- ... 300+ more lines ... -->
  <!-- </editor-fold> -->
</idea-plugin>
```

**Problems**:
- Class names inlined before scrambling ❌
- Massive file duplication
- Hard to maintain

### After (Dynamic References)

```xml
<!-- CLionPlugin.xml: Clean xi:include references -->
<idea-plugin>
  <xi:include href="META-INF/PlatformLangPlugin.xml"/>
  <xi:include href="intellij.platform.remoteServers.impl.xml"/>
  <xi:include href="META-INF/ultimate.xml"/>
</idea-plugin>
```

**Benefits**:
- No premature inlining ✓
- Content embedded only when needed (scrambled code via plugin-classpath.txt)
- Smaller, maintainable files ✓
- Scrambled class names when embedded ✓

## Test Verification

**Location**: `build/testSrc/.../IdeaUltimateBuildTest.kt`

The test verifies both issues are resolved:

```kotlin
// 1. Verify product descriptor doesn't contain unscrambled class names
val xmlContent = cachedXml.decodeToString()
assertThat(xmlContent).doesNotContain("com.intellij.ide.todo.TodoConfiguration")
assertThat(xmlContent).doesNotContain("com.intellij.ide.bookmarks.Bookmark")

// 2. Verify plugin descriptor doesn't contain unscrambled class names
val pluginContent = (distFiles.first().content as InMemoryDistFileContent).data.decodeToString()
assertThat(pluginContent).doesNotContain(
  "com.intellij.cwm.connection.backend.license.OpenLicenseSettingsAction"
)
```

## Developer Guide

### How to Determine if Your Plugin Needs Scrambling-Aware Embedding

**Check 1: Does your plugin configure scrambling?**

Look for `pathsToScramble` in your plugin's layout configuration:

```kotlin
// In your PluginLayout builder:
pluginLayout {
  mainModule = "your.plugin.main"
  pathsToScramble = listOf("lib/your-plugin.jar")
  // If pathsToScramble is non-empty, you need scrambling-aware embedding
}
```

**Check 2: Does your plugin have content modules?**

Look for `<content>` tags in your `plugin.xml`:

```xml
<!-- In your plugin.xml -->
<content>
  <module name="your.plugin.core"/>
  <module name="your.plugin.optional"/>
</content>
```

**Result**: If BOTH checks are true, your plugin uses **Path 2** (scrambled embedding via plugin-classpath.txt).

### How to Verify Scrambled Class Names Are Embedded

**Step 1: Build with scrambling enabled**

```bash
# Full distribution build
./gradlew buildDistribution

# Or plugin-specific build
./gradlew :intellij.idea.ultimate.build:buildPlugin
```

**Step 2: Check the build output**

```bash
cd build/dist/plugins/YourPlugin/lib

# plugin-classpath.txt should exist
ls -la plugin-classpath.txt
```

**Step 3: Inspect the descriptor cache (for debugging)**

Add temporary logging in `embedContentModules()`:

```kotlin
// In classPath/contentModuleEmbedding.kt
val cachedData = pluginDescriptorContainer.getCachedFileData(descriptorFilename)
println("Module: $moduleName")
println("Descriptor content: ${cachedData?.decodeToString()?.take(500)}")
// Should see scrambled class names like "com.a.b.c" instead of "com.example.Foo"
```

**Step 4: Verify at runtime**

Run the IDE with the scrambled plugin and check:
- Plugin loads successfully
- No `ClassNotFoundException` errors
- Features work as expected

### Common Mistakes

#### Mistake 1: Embedding Too Early

**Wrong**: Manually embedding before scrambling

```kotlin
// DON'T DO THIS:
fun layout() {
  embedContentModules(...)  // Uses original class names!
  scramble(...)            // Too late - already embedded
}
```

**Right**: Let the build system handle timing

The build system automatically:
- Non-scrambled plugins: PluginXmlPatcher embeds during Layout
- Scrambled plugins: classpath.kt embeds after Scrambling

#### Mistake 2: Not Updating pathsToScramble

**Wrong**: Added content module but forgot to scramble its JAR

```kotlin
pluginLayout {
  pathsToScramble = listOf("lib/main.jar")
  // Added new content module:
  content {
    module("new.module")  // Will be in lib/modules/new.module.jar
  }
  // Problem: new.module.jar is NOT in pathsToScramble!
}
```

**Right**: Scramble all JARs with scrambled code

```kotlin
pluginLayout {
  pathsToScramble = listOf(
    "lib/main.jar",
    "lib/modules/new.module.jar"  // Don't forget this!
  )
}
```

#### Mistake 3: Assuming Cache is Read-Only

**Wrong**: Caching descriptor content across phases

```kotlin
// DON'T DO THIS:
val descriptorContent = cache.get("plugin.xml")  // During Layout
// ... scrambling happens ...
useDescriptor(descriptorContent)  // Uses OLD content!
```

**Right**: Always read from cache when needed

The cache is updated by scrambling, so always read fresh:

```kotlin
// After scrambling, read from cache again
val currentDescriptor = cache.get("plugin.xml")  // Gets scrambled version
```

### Debugging Tips

#### Enable Verbose Logging

```bash
# For Gradle builds
./gradlew buildDistribution -Dintellij.build.verbose=true

# For tests
./tests.cmd -Dintellij.build.verbose=true \
  -Dintellij.build.test.patterns=ProductModulesXmlConsistencyTest
```

#### Check Scrambling Logs

Scrambling logs contain useful information about what was processed:

```bash
# Find scrambling logs
find build/artifacts -name "scramble-logs" -type d

# Extract and view
cd build/artifacts/scramble-logs
unzip -l your-plugin.zip
```

#### Verify Cache Updates

Add breakpoint or logging in:
- `ZkmScrambleTool.kt::updatePackageIndexUsingTempFile()` (line 513)
- Check what XML files are being extracted and cached

#### Compare Before/After Descriptors

```bash
# Extract unscrambled JAR (before scrambling)
cd build/dist-unscrambled/plugins/YourPlugin
unzip -p lib/your-plugin.jar META-INF/plugin.xml > before.xml

# Extract scrambled JAR (after scrambling)
cd build/dist/plugins/YourPlugin
unzip -p lib/your-plugin.jar META-INF/plugin.xml > after.xml

# Compare class names
grep -o 'class="[^"]*"' before.xml | head -10  # Original names
grep -o 'class="[^"]*"' after.xml | head -10   # Scrambled names
```

#### Use ProductModulesXmlConsistencyTest

Run the consistency test to verify no unscrambled names leak:

```bash
./tests.cmd \
  -Dintellij.build.clean.output.root=false \
  -Dintellij.build.incremental.compilation=true \
  -Dintellij.build.test.patterns=ProductModulesXmlConsistencyTest
```

This test automatically checks:
- Product descriptors don't contain unscrambled class names
- Plugin descriptors in plugin-classpath.txt are properly scrambled
- Content modules are correctly embedded

### Troubleshooting Common Issues

#### Issue: ClassNotFoundException at Runtime

**Symptom**: Plugin fails to load with `ClassNotFoundException: com.example.OriginalClassName`

**Cause**: Content module was embedded before scrambling with original class names, but the actual class was renamed.

**Solution**:
1. Verify `pathsToScramble` includes the module's JAR
2. Check that embedding happens AFTER scrambling (Path 2)
3. Verify cache was updated by scrambling

#### Issue: Plugin Loads Slowly

**Symptom**: Long startup time, many disk I/O operations

**Cause**: Content modules not embedded in plugin-classpath.txt, falling back to runtime loading.

**Solution**:
1. Verify plugin-classpath.txt exists in `lib/` directory
2. Check that `pathsToScramble` is configured correctly
3. Ensure `embedContentModules()` is being called in classpath.kt

#### Issue: Build Fails with "descriptor not found in cache"

**Symptom**: Build error: "Could not find descriptor for module X in cache"

**Cause**: Descriptor wasn't cached during Layout phase.

**Solution**:
1. Check that the module's JAR is being packaged correctly
2. Verify JAR contains `META-INF/plugin.xml` or the expected descriptor
3. Check JarPackager is caching descriptors properly

### Best Practices

1. **Always configure pathsToScramble for closed-source plugins**
   - Include all JARs that contain scrambled code
   - Don't forget content module JARs

2. **Test both scrambled and non-scrambled builds**
   - Verify plugin works with scrambling enabled
   - Verify plugin works with scrambling disabled
   - Use automated tests (ProductModulesXmlConsistencyTest)

3. **Monitor cache updates during development**
   - Add logging to understand cache behavior
   - Verify scrambling updates the cache correctly
   - Check cache is read after scrambling

4. **Use incremental builds for faster iteration**
   ```bash
   ./tests.cmd \
     -Dintellij.build.clean.output.root=false \
     -Dintellij.build.incremental.compilation=true
   ```

5. **Document scrambling requirements**
   - Note which modules must be scrambled
   - Explain why scrambling is needed
   - Update documentation when adding content modules

## Summary

The scrambling-aware architecture solves both issues through a **dual-path approach**:

### For Product Modules (PlatformLayout):
1. **Scrambled embedded modules**: Don't embed → use plugin-classpath.txt at runtime
2. **Non-scrambled modules**: Embed now → xi:includes need resolution for separate classloader

### For Plugin Content Modules (PluginLayout):
1. **Scrambled plugins** (`!pathsToScramble.isEmpty()`): Embed in plugin-classpath.txt after scrambling
2. **Non-scrambled plugins** (`pathsToScramble.isEmpty()`): Embed in plugin.xml during patching

### Key Benefits:
- ✓ Scrambled class names appear in CDATA sections (read from post-scrambling cache)
- ✓ Files modified by scrambling are respected (conditional embedding)
- ✓ Product XML files remain small and maintainable (xi:include references)
- ✓ Non-scrambled builds work efficiently (immediate embedding or runtime resolution)

**Build Pipeline Order**: `Compilation → Layout (with caching) → Scrambling → Conditionally Embed (dual path) → Distribution`

This architecture ensures correctness (scrambled names preserved) while maintaining optimal performance (pre-computed metadata).
