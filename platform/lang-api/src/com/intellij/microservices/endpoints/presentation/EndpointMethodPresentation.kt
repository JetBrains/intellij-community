package com.intellij.microservices.endpoints.presentation

import com.intellij.microservices.endpoints.EndpointsProvider

/**
 * Provides additional method attribute for endpoint presentation in Endpoints View  if it is applicable for [EndpointsProvider].
 */
interface EndpointMethodPresentation {
  /**
   * @return presentation of endpoint method, e.g. HTTP request method
   */
  val endpointMethodPresentation: String?

  /**
   * @return list of HTTP verbs supported by endpoint
   */
  val endpointMethods: List<String>

  /**
   * @return order of endpoint method for sorting
   */
  val endpointMethodOrder: Int
}