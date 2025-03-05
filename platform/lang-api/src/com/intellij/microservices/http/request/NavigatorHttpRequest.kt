package com.intellij.microservices.http.request

import com.intellij.openapi.util.Pair

class NavigatorHttpRequest(val url: String,
                           val requestMethod: String,
                           val headers: List<Pair<String, String>>,
                           val params: List<Pair<String, String>>)