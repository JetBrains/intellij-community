package org.jetbrains.ide;

import com.google.gson.stream.JsonReader;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.JBProtocolCommand;
import com.intellij.openapi.application.JetBrainsProtocolHandler;
import com.intellij.openapi.application.ModalityState;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Konstantin Bulenkov
 */
public class JetBrainsProtocolHandlerHttpService extends RestService {
  private static final String URL_PARAM_NAME = "url";

  @NotNull
  @Override
  protected String getServiceName() {
    return "internal";
  }

  @Override
  protected boolean isMethodSupported(@NotNull HttpMethod method) {
    return method == HttpMethod.POST;
  }

  @Nullable
  @Override
  public String execute(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context) throws IOException {
    final JsonReader reader = createJsonReader(request);
    reader.beginObject();
    final String name = reader.nextName();
    final String url = reader.nextString();
    reader.endObject();

    activateLastFocusedFrame();

    if (URL_PARAM_NAME.equals(name) && url != null && url.startsWith(JetBrainsProtocolHandler.PROTOCOL)) {
      JetBrainsProtocolHandler.processJetBrainsLauncherParameters(url);
      ApplicationManager.getApplication().invokeLater(JBProtocolCommand::handleCurrentCommand, ModalityState.any());
    }

    sendOk(request, context);
    return null;
  }
}
