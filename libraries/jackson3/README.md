# Jackson 3 library modules

These modules wrap Jackson 3 (`tools.jackson.*`). The Jackson 2 wrappers live in `../jackson/`.
Both stacks share `intellij.libraries.jackson.annotations` (`com.fasterxml.jackson.annotation`).
New code uses Jackson 3. Move an existing module with the steps below.

## Move a module

1. In the `.iml`, rename each order entry: `intellij.libraries.jackson` to `intellij.libraries.jackson3`,
   `.jackson.databind` to `.jackson3.databind`, `.jackson.module.kotlin` to `.jackson3.module.kotlin`,
   `.jackson.dataformat.yaml` to `.jackson3.dataformat.yaml`, `.jackson.jr.objects` to `.jackson3.jr.objects`.
   Remove `.jackson.datatype.jsr310` and `.jackson.datatype.jdk8`. Databind 3 contains them.
2. Apply the same names in the module descriptor under `resources/`.
3. Run `./build/jpsModelToBazel.cmd`.
4. Port the sources with the table below.
5. Run the module tests and `./tests.cmd --module intellij.projectStructureTests --test 'com.intellij.ideaProjectStructure.fast.*'`.

## Renames

| Jackson 2 | Jackson 3 |
|---|---|
| `ObjectMapper()`, `jacksonObjectMapper()` | `JsonMapper.builder()...build()`, `jacksonMapperBuilder()...build()` |
| `mapper.configure(...)`, `setDateFormat`, `setVisibility` | the same calls on the builder; the mapper is immutable |
| `configure(FAIL_ON_UNKNOWN_PROPERTIES, false)` | delete; it is the default |
| `VisibilityChecker.Std(...)` | `changeDefaultVisibility { it.withFieldVisibility(...) }` |
| `setSerializationInclusion(x)` | `changeDefaultPropertyInclusion { JsonInclude.Value.construct(x, x) }` |
| `JsonDeserializer`, `JsonSerializer`, `SerializerProvider` | `ValueDeserializer`, `ValueSerializer`, `SerializationContext` |
| `parser.codec.readTree(p)` | `ctxt.readTree(p)`, `ctxt.readTreeAsValue(node, type)` |
| `JsonProcessingException`, `JsonParseException` | `JacksonException`, `StreamReadException` |
| `DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE` | `EnumFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE` |
| `PropertyNamingStrategy.SNAKE_CASE` | `PropertyNamingStrategies.SNAKE_CASE` |
| `mapper.serializationConfig.introspect(type)` | `config.classIntrospectorInstance().introspectForSerialization(type, ac)` |
| `JsonNode.asText()`, `isTextual`, `fields()`, `fieldNames()`, `elements()` | `asString()`, `isString`, `properties()`, `propertyNames()`, `values()` |
| `JsonNode.textValue()` | `stringValue(null)`; `stringValue()` throws on a missing or non-string node |
| `JsonToken.FIELD_NAME`, `parser.text`, `JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES` | `JsonToken.PROPERTY_NAME`, `parser.string`, `JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES` |
| `writeFieldName`, `writeStringField` | `writeName`, `writeStringProperty` |
| `factory.createParser(x)` | `factory.createParser(ObjectReadContext.empty(), x)` |
| `com.fasterxml.jackson.core.JsonFactory` | `tools.jackson.core.json.JsonFactory` |

## Traps

- A parse error is unchecked and is not an `IOException`. Add `catch (JacksonException)` next to `catch (IOException)`.
- `@get:JsonProperty` does not drive deserialization. Use `@param:JsonProperty` on a constructor property.
- `JsonNode.map { }` is a member that maps the node itself. Use `node.values().map { }`.
- `NullNode.asText()` returned `"null"`; `asString()` returns `""`. Write the literal when the text must stay `null`.
- The Kotlin module enables `KotlinFeature.StrictNullChecks` by default. A `null` inside a `List<String>` now fails.
  Use `jacksonMapperBuilder { disable(KotlinFeature.StrictNullChecks) }` when the data source may send such nulls.
- `FAIL_ON_NULL_FOR_PRIMITIVES` is on. A Kotlin data class with a non-nullable `Boolean` or `Int` creator
  parameter fails on a JSON file that omits it. Disable the feature on the builder, or give the parameter a default.
- `ALLOW_FINAL_FIELDS_AS_MUTATORS` is off. A body `val` of a data class is no longer filled from the JSON.
- `SORT_PROPERTIES_ALPHABETICALLY` is on. Serialized output changes order. Disable it when a gold file or a
  hand-edited file fixes the order.
- Dates are written as ISO strings by default. Set `WRITE_DATES_AS_TIMESTAMPS` when the format must not change.
- `com.intellij.util.io.jackson.JacksonUtil` in `intellij.platform.ide.util.io.impl` hides `ObjectWriteContext` for streaming.
