package com.intellij.microservices.oas

data class OasSchema(
  val type: OasSchemaType? = null,
  val format: OasSchemaFormat? = null,
  val default: String? = null,
  val items: OasSchema? = null,
  val properties: List<OasProperty>? = null,
  val reference: String? = null,
  val enum: List<String>? = null,
  val required: List<String>? = null,
  val isNullable: Boolean = false
) {
  class Builder(val type: OasSchemaType) {
    var format: OasSchemaFormat? = null
    var reference: String? = null
    var items: OasSchema? = null
    var properties: MutableMap<String, OasSchema> = mutableMapOf()

    @JvmOverloads
    fun build(block: (Builder.() -> Unit)? = null): OasSchema {
      block?.invoke(this)
      return OasSchema(type, format, reference, items, properties.map { (name, schema) -> OasProperty(name, schema) })
    }
  }
}