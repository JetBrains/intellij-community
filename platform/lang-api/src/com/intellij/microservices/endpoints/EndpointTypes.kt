@file:JvmName("EndpointTypes")

package com.intellij.microservices.endpoints

import com.intellij.icons.AllIcons
import com.intellij.microservices.MicroservicesBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier
import javax.swing.Icon

/**
 * Describes available types of endpoints.
 */
class EndpointType(
  /**
   * Identifier for search field of Endpoints View. Prefer Title-Case-With-Dashes format.
   */
  @get:NonNls
  val queryTag: String,
  val icon: Icon?,
  val localizedMessage: Supplier<@Nls String>
)

@JvmField
val XML_WEB_SERVICE_TYPE: EndpointType = EndpointType("XML-Web-Service", AllIcons.Webreferences.Server,
                                                      MicroservicesBundle.messagePointer("EndpointType.XML_WEB_SERVICE"))

@JvmField
val HTTP_SERVER_TYPE: EndpointType = EndpointType("HTTP-Server", AllIcons.Webreferences.Server,
                                                  MicroservicesBundle.messagePointer("EndpointType.HTTP_SERVER"))

@JvmField
val HTTP_CLIENT_TYPE: EndpointType = EndpointType("HTTP-Client", AllIcons.Javaee.WebServiceClient,
                                                  MicroservicesBundle.messagePointer("EndpointType.HTTP_CLIENT"))

@JvmField
val HTTP_MOCK_TYPE: EndpointType = EndpointType("HTTP-Mock-Server", AllIcons.Webreferences.Server,
                                                MicroservicesBundle.messagePointer("EndpointType.HTTP_MOCK_SERVER"))

@JvmField
val WEBSOCKET_SERVER_TYPE: EndpointType = EndpointType("WebSocket-Server", AllIcons.Webreferences.Server,
                                                       MicroservicesBundle.messagePointer("EndpointType.WEBSOCKET_SERVER"))

@JvmField
val GRAPH_QL_TYPE: EndpointType = EndpointType("Graph-QL", AllIcons.Webreferences.Server,
                                               MicroservicesBundle.messagePointer("EndpointType.GRAPH_QL"))

@JvmField
val WEBSOCKET_CLIENT_TYPE: EndpointType = EndpointType("WebSocket-Client", AllIcons.Javaee.WebServiceClient,
                                                       MicroservicesBundle.messagePointer("EndpointType.WEBSOCKET_CLIENT"))

@JvmField
val API_DEFINITION_TYPE: EndpointType = EndpointType("API-Definition", AllIcons.FileTypes.Config,
                                                     MicroservicesBundle.messagePointer("EndpointType.API_DEFINITION"))