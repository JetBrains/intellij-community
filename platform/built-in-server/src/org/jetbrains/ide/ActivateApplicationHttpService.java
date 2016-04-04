package org.jetbrains.ide;

import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.AppIcon;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * @author Konstantin Bulenkov
 */
public class ActivateApplicationHttpService extends RestService {
  @NotNull
  @Override
  protected String getServiceName() {
    return "show";
  }

  @Override
  protected boolean isMethodSupported(@NotNull HttpMethod method) {
    return method == HttpMethod.GET;
  }

  @Nullable
  @Override
  public String execute(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context)
    throws IOException {
    final IdeFrame frame = IdeFocusManager.findInstance().getLastFocusedFrame();
    if (frame instanceof Window) {
      sendOk(request, context);
      Runnable runnable = new Runnable() {
        @Override
        public void run() {
          Window window = (Window)frame;
          window.toFront();
          window.requestFocusInWindow();

          AppIcon.getInstance().requestFocus(frame);

        }
      };
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(runnable);
      return "Success";
    }
    sendStatus(HttpResponseStatus.NOT_FOUND, false, context.channel());
    return "Can't find IDE Frame";
  }
}
