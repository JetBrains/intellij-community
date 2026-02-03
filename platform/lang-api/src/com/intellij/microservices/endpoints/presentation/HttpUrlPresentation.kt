package com.intellij.microservices.endpoints.presentation

import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.editor.colors.TextAttributesKey
import javax.swing.Icon

class HttpUrlPresentation(httpUrl: String?,
                          definitionSource: String?,
                          icon: Icon?,
                          attributesKey: TextAttributesKey?) : PresentationData(httpUrl, definitionSource, icon, attributesKey) {

  constructor(httpUrl: String?,
              definitionSource: String?,
              icon: Icon?) : this(httpUrl, definitionSource, icon, null)
}