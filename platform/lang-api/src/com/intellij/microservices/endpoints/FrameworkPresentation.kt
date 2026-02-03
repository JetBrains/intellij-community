package com.intellij.microservices.endpoints

import com.intellij.openapi.util.NlsSafe
import javax.swing.Icon

class FrameworkPresentation(
  /**
   * Provider id for search field of Endpoints View: prefer Title-Case-With-Dashes format.
   */
  @NlsSafe
  val queryTag: String,
  @NlsSafe
  val title: String,
  val icon: Icon?
)