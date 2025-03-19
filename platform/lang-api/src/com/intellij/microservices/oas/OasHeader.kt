package com.intellij.microservices.oas

data class OasHeader(val name: String, val required: Boolean, val schema: OasSchema)