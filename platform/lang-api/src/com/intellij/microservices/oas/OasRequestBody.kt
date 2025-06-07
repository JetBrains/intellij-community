package com.intellij.microservices.oas

class OasRequestBody(val content: Map<String, OasSchema>,
                     val required: Boolean)