// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.*;
import org.cef.handler.*;
import org.cef.misc.BoolRef;
import org.cef.network.CefRequest;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A wrapper over {@link CefClient}.
 * <p>
 * Provides facilities to add multiple handlers of the same type ({@code CefClient} doesn't).
 * All the handlers of the same type are called in the "last-added-last-called" order. When a return value is expected, the last handler's
 * return value is returned as the result. When a CEF callback is passed to the handler, it's the responsibility of the client to manage
 * the callback calls in case when multiple handlers are added.
 *
 * @author tav
 */
// [tav]: todo: think if we need some more sophisticated way to handle results of sequence of handles (like foldResults() callback)
@SuppressWarnings({"unused", "UnusedReturnValue"}) // [tav] todo: remove it ( or add*Handler methods not yet used)
@ApiStatus.Experimental
public class JBCefClient implements JBCefDisposable {
  private static final Logger LOG = Logger.getInstance(JBCefClient.class);

  @NotNull private final CefClient myCefClient;
  @NotNull private final DisposeHelper myDisposeHelper = new DisposeHelper();

  private final HandlerSupport<CefContextMenuHandler> myContextMenuHandler = new HandlerSupport<>();
  private final HandlerSupport<CefDialogHandler> myDialogHandler = new HandlerSupport<>();
  private final HandlerSupport<CefDisplayHandler> myDisplayHandler = new HandlerSupport<>();
  private final HandlerSupport<CefDownloadHandler> myDownloadHandler = new HandlerSupport<>();
  private final HandlerSupport<CefDragHandler> myDragHandler = new HandlerSupport<>();
  private final HandlerSupport<CefFocusHandler> myFocusHandler = new HandlerSupport<>();
  private final HandlerSupport<CefJSDialogHandler> myJSDialogHandler = new HandlerSupport<>();
  private final HandlerSupport<CefKeyboardHandler> myKeyboardHandler = new HandlerSupport<>();
  private final HandlerSupport<CefLifeSpanHandler> myLifeSpanHandler = new HandlerSupport<>();
  private final HandlerSupport<CefLoadHandler> myLoadHandler = new HandlerSupport<>();
  private final HandlerSupport<CefRequestHandler> myRequestHandler = new HandlerSupport<>();

  JBCefClient(@NotNull CefClient client) {
    myCefClient = client;
    Disposer.register(JBCefApp.getInstance().getDisposable(), this);
  }

  @NotNull
  public CefClient getCefClient() {
    return myCefClient;
  }

  @Override
  public void dispose() {
    myDisposeHelper.dispose(() -> {
      try {
        myCefClient.dispose();
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    });
  }

  @Override
  public boolean isDisposed() {
    return myDisposeHelper.isDisposed();
  }

  public JBCefClient addContextMenuHandler(@NotNull CefContextMenuHandler handler, @NotNull CefBrowser browser) {
    return myContextMenuHandler.add(handler, browser, () -> {
      myCefClient.addContextMenuHandler(new CefContextMenuHandler() {
        @Override
        public void onBeforeContextMenu(CefBrowser browser, CefFrame frame, CefContextMenuParams params, CefMenuModel model) {
          myContextMenuHandler.handle(browser, handler -> {
            handler.onBeforeContextMenu(browser, frame, params, model);
          });
        }

        @Override
        public boolean onContextMenuCommand(CefBrowser browser,
                                            CefFrame frame,
                                            CefContextMenuParams params,
                                            int commandId,
                                            int eventFlags) {
          return myContextMenuHandler.handleNotNull(browser, handler -> {
            return handler.onContextMenuCommand(browser, frame, params, commandId, eventFlags);
          });
        }

        @Override
        public void onContextMenuDismissed(CefBrowser browser, CefFrame frame) {
          myContextMenuHandler.handle(browser, handler -> {
            handler.onContextMenuDismissed(browser, frame);
          });
        }
      });
    });
  }

  public void removeContextMenuHandler(@NotNull CefContextMenuHandler handler, @NotNull CefBrowser browser) {
    myContextMenuHandler.remove(handler, browser, () -> myCefClient.removeContextMenuHandler());
  }

  public JBCefClient addDialogHandler(@NotNull CefDialogHandler handler, @NotNull CefBrowser browser) {
    return myDialogHandler.add(handler, browser, () -> {
      myCefClient.addDialogHandler(new CefDialogHandler() {
        @Override
        public boolean onFileDialog(CefBrowser browser,
                                    FileDialogMode mode,
                                    String title,
                                    String defaultFilePath,
                                    Vector<String> acceptFilters,
                                    int selectedAcceptFilter,
                                    CefFileDialogCallback callback) {
          return myDialogHandler.handleNotNull(browser, handler -> {
            return handler.onFileDialog(browser, mode, title, defaultFilePath, acceptFilters, selectedAcceptFilter, callback);
          });
        }
      });
    });
  }

  public void removeDialogHandler(@NotNull CefDialogHandler handler, @NotNull CefBrowser browser) {
    myDialogHandler.remove(handler, browser, () -> myCefClient.removeDialogHandler());
  }

  public JBCefClient addDisplayHandler(@NotNull CefDisplayHandler handler, @NotNull CefBrowser browser) {
    return myDisplayHandler.add(handler, browser, () -> {
      myCefClient.addDisplayHandler(new CefDisplayHandler() {
        @Override
        public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
          myDisplayHandler.handle(browser, handler -> {
            handler.onAddressChange(browser, frame, url);
          });
        }

        @Override
        public void onTitleChange(CefBrowser browser, String title) {
          myDisplayHandler.handle(browser, handler -> {
            handler.onTitleChange(browser, title);
          });
        }

        @Override
        public boolean onTooltip(CefBrowser browser, String text) {
          return myDisplayHandler.handleNotNull(browser, handler -> {
            return handler.onTooltip(browser, text);
          });
        }

        @Override
        public void onStatusMessage(CefBrowser browser, String value) {
          myDisplayHandler.handle(browser, handler -> {
            handler.onStatusMessage(browser, value);
          });
        }

        @Override
        public boolean onConsoleMessage(CefBrowser browser, CefSettings.LogSeverity level, String message, String source, int line) {
          return myDisplayHandler.handleNotNull(browser, handler -> {
            return handler.onConsoleMessage(browser, level, message, source, line);
          });
        }
      });
    });
  }

  public void removeDisplayHandler(@NotNull CefDisplayHandler handler, @NotNull CefBrowser browser) {
    myDisplayHandler.remove(handler, browser, () -> myCefClient.removeDisplayHandler());
  }

  public JBCefClient addDownloadHandler(@NotNull CefDownloadHandler handler, @NotNull CefBrowser browser) {
    return myDownloadHandler.add(handler, browser, () -> {
      myCefClient.addDownloadHandler(new CefDownloadHandler() {
        @Override
        public void onBeforeDownload(CefBrowser browser,
                                     CefDownloadItem downloadItem,
                                     String suggestedName,
                                     CefBeforeDownloadCallback callback) {
          myDownloadHandler.handle(browser, handler -> {
            handler.onBeforeDownload(browser, downloadItem, suggestedName, callback);
          });
        }

        @Override
        public void onDownloadUpdated(CefBrowser browser, CefDownloadItem downloadItem, CefDownloadItemCallback callback) {
          myDownloadHandler.handle(browser, handler -> {
            handler.onDownloadUpdated(browser, downloadItem, callback);
          });
        }
      });
    });
  }

  public void removeDownloadHandle(@NotNull CefDownloadHandler handler, @NotNull CefBrowser browser) {
    myDownloadHandler.remove(handler, browser, () -> myCefClient.removeDownloadHandler());
  }

  public JBCefClient addDragHandler(@NotNull CefDragHandler handler, @NotNull CefBrowser browser) {
    return myDragHandler.add(handler, browser, () -> {
      myCefClient.addDragHandler(new CefDragHandler() {
        @Override
        public boolean onDragEnter(CefBrowser browser, CefDragData dragData, int mask) {
          return myDragHandler.handleNotNull(browser, handler -> {
            return handler.onDragEnter(browser, dragData, mask);
          });
        }
      });
    });
  }

  public void removeDragHandler(@NotNull CefDragHandler handler, @NotNull CefBrowser browser) {
    myDragHandler.remove(handler, browser, () -> myCefClient.removeDragHandler());
  }

  public JBCefClient addFocusHandler(@NotNull CefFocusHandler handler, @NotNull CefBrowser browser) {
    return myFocusHandler.add(handler, browser, () -> {
      myCefClient.addFocusHandler(new CefFocusHandler() {
        @Override
        public void onTakeFocus(CefBrowser browser, boolean next) {
          myFocusHandler.handle(browser, handler -> {
            handler.onTakeFocus(browser, next);
          });
        }

        @Override
        public boolean onSetFocus(CefBrowser browser, FocusSource source) {
          return myFocusHandler.handleNotNull(browser, handler -> {
            return handler.onSetFocus(browser, source);
          });
        }

        @Override
        public void onGotFocus(CefBrowser browser) {
          myFocusHandler.handle(browser, handler -> {
            handler.onGotFocus(browser);
          });
        }
      });
    });
  }

  public void removeFocusHandler(@NotNull CefFocusHandler handler, @NotNull CefBrowser browser) {
    myFocusHandler.remove(handler, browser, () -> myCefClient.removeFocusHandler());
  }

  public JBCefClient addJSDialogHandler(@NotNull CefJSDialogHandler handler, @NotNull CefBrowser browser) {
    return myJSDialogHandler.add(handler, browser, () -> {
      myCefClient.addJSDialogHandler(new CefJSDialogHandler() {
        @Override
        public boolean onJSDialog(CefBrowser browser,
                                  String origin_url,
                                  JSDialogType dialog_type,
                                  String message_text,
                                  String default_prompt_text,
                                  CefJSDialogCallback callback,
                                  BoolRef suppress_message) {
          return myJSDialogHandler.handleNotNull(browser, handler -> {
            return handler.onJSDialog(browser, origin_url, dialog_type, message_text, default_prompt_text, callback, suppress_message);
          });
        }

        @Override
        public boolean onBeforeUnloadDialog(CefBrowser browser, String message_text, boolean is_reload, CefJSDialogCallback callback) {
          return myJSDialogHandler.handleNotNull(browser, handler -> {
            return handler.onBeforeUnloadDialog(browser, message_text, is_reload, callback);
          });
        }

        @Override
        public void onResetDialogState(CefBrowser browser) {
          myJSDialogHandler.handle(browser, handler -> {
            handler.onResetDialogState(browser);
          });
        }

        @Override
        public void onDialogClosed(CefBrowser browser) {
          myJSDialogHandler.handle(browser, handler -> {
            handler.onDialogClosed(browser);
          });
        }
      });
    });
  }

  public void removeJSDialogHandler(@NotNull CefJSDialogHandler handler, @NotNull CefBrowser browser) {
    myJSDialogHandler.remove(handler, browser, () -> myCefClient.removeJSDialogHandler());
  }

  public JBCefClient addKeyboardHandler(@NotNull CefKeyboardHandler handler, @NotNull CefBrowser browser) {
    return myKeyboardHandler.add(handler, browser, () -> {
      myCefClient.addKeyboardHandler(new CefKeyboardHandler() {
        @Override
        public boolean onPreKeyEvent(CefBrowser browser, CefKeyEvent event, BoolRef is_keyboard_shortcut) {
          return myKeyboardHandler.handleNotNull(browser, handler -> {
            return handler.onPreKeyEvent(browser, event, is_keyboard_shortcut);
          });
        }

        @Override
        public boolean onKeyEvent(CefBrowser browser, CefKeyEvent event) {
          return myKeyboardHandler.handleNotNull(browser, handler -> {
            return handler.onKeyEvent(browser, event);
          });
        }
      });
    });
  }

  public void removeKeyboardHandler(@NotNull CefKeyboardHandler handler, @NotNull CefBrowser browser) {
    myKeyboardHandler.remove(handler, browser, () -> myCefClient.removeKeyboardHandler());
  }

  public JBCefClient addLifeSpanHandler(@NotNull CefLifeSpanHandler handler, @NotNull CefBrowser browser) {
    return myLifeSpanHandler.add(handler, browser, () -> {
      myCefClient.addLifeSpanHandler(new CefLifeSpanHandler() {
        @Override
        public boolean onBeforePopup(CefBrowser browser, CefFrame frame, String target_url, String target_frame_name) {
          return myLifeSpanHandler.handleNotNull(browser, handler -> {
            return handler.onBeforePopup(browser, frame, target_url, target_frame_name);
          });
        }

        @Override
        public void onAfterCreated(CefBrowser browser) {
          myLifeSpanHandler.handle(browser, handler -> {
            handler.onAfterCreated(browser);
          });
        }

        @Override
        public void onAfterParentChanged(CefBrowser browser) {
          myLifeSpanHandler.handle(browser, handler -> {
            handler.onAfterParentChanged(browser);
          });
        }

        @Override
        public boolean doClose(CefBrowser browser) {
          return myLifeSpanHandler.handleNotNull(browser, handler -> {
            return handler.doClose(browser);
          });
        }

        @Override
        public void onBeforeClose(CefBrowser browser) {
          myLifeSpanHandler.handle(browser, handler -> {
            handler.onBeforeClose(browser);
          });
        }
      });
    });
  }

  public void removeLifeSpanHandler(@NotNull CefLifeSpanHandler handler, @NotNull CefBrowser browser) {
    myLifeSpanHandler.remove(handler, browser, () -> myCefClient.removeLifeSpanHandler());
  }

  public JBCefClient addLoadHandler(@NotNull CefLoadHandler handler, @NotNull CefBrowser browser) {
    return myLoadHandler.add(handler, browser, () -> {
      myCefClient.addLoadHandler(new CefLoadHandler() {
        @Override
        public void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
          myLoadHandler.handle(browser, handler -> {
            handler.onLoadingStateChange(browser, isLoading, canGoBack, canGoForward);
          });
        }

        @Override
        public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transitionType) {
          myLoadHandler.handle(browser, handler -> {
            handler.onLoadStart(browser, frame, transitionType);
          });
        }

        @Override
        public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
          myLoadHandler.handle(browser, handler -> {
            handler.onLoadEnd(browser, frame, httpStatusCode);
          });
        }

        @Override
        public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode, String errorText, String failedUrl) {
          myLoadHandler.handle(browser, handler -> {
            handler.onLoadError(browser, frame, errorCode, errorText, failedUrl);
          });
        }
      });
    });
  }

  public void removeLoadHandler(@NotNull CefLoadHandler handler, @NotNull CefBrowser browser) {
    myLoadHandler.remove(handler, browser, () -> myCefClient.removeLoadHandler());
  }

  public JBCefClient addRequestHandler(@NotNull CefRequestHandler handler, @NotNull CefBrowser browser) {
    return myRequestHandler.add(handler, browser, () -> {
      myCefClient.addRequestHandler(new CefRequestHandler() {
        @Override
        public boolean onBeforeBrowse(CefBrowser browser, CefFrame frame, CefRequest request, boolean user_gesture, boolean is_redirect) {
          return myRequestHandler.handleNotNull(browser, handler -> {
            return handler.onBeforeBrowse(browser, frame, request, user_gesture, is_redirect);
          });
        }

        @Override
        public CefResourceRequestHandler getResourceRequestHandler(CefBrowser browser,
                                                                   CefFrame frame,
                                                                   CefRequest request,
                                                                   boolean isNavigation,
                                                                   boolean isDownload,
                                                                   String requestInitiator,
                                                                   BoolRef disableDefaultHandling)
        {
          return myRequestHandler.handleNotNull(browser, handler -> {
            return handler.getResourceRequestHandler(browser, frame, request, isNavigation, isDownload, requestInitiator, disableDefaultHandling);
          });
        }

        @Override
        public boolean getAuthCredentials(CefBrowser browser,
                                          String origin_url,
                                          boolean isProxy,
                                          String host,
                                          int port,
                                          String realm,
                                          String scheme,
                                          CefAuthCallback callback)
        {
          return myRequestHandler.handleNotNull(browser, handler -> {
            return handler.getAuthCredentials(browser, origin_url, isProxy, host, port, realm, scheme, callback);
          });
        }

        @Override
        public boolean onQuotaRequest(CefBrowser browser, String origin_url, long new_size, CefRequestCallback callback) {
          return myRequestHandler.handleNotNull(browser, handler -> {
            return handler.onQuotaRequest(browser, origin_url, new_size, callback);
          });
        }

        @Override
        public boolean onCertificateError(CefBrowser browser,
                                          CefLoadHandler.ErrorCode cert_error,
                                          String request_url,
                                          CefRequestCallback callback) {
          return myRequestHandler.handleNotNull(browser, handler -> {
            return handler.onCertificateError(browser, cert_error, request_url, callback);
          });
        }

        @Override
        public void onPluginCrashed(CefBrowser browser, String pluginPath) {
          myRequestHandler.handle(browser, handler -> {
            handler.onPluginCrashed(browser, pluginPath);
          });
        }

        @Override
        public void onRenderProcessTerminated(CefBrowser browser, TerminationStatus status) {
          myRequestHandler.handle(browser, handler -> {
            handler.onRenderProcessTerminated(browser, status);
          });
        }
      });
    });
  }

  public void removeRequestHandler(@NotNull CefRequestHandler handler, @NotNull CefBrowser browser) {
    myRequestHandler.remove(handler, browser, () -> myCefClient.removeRequestHandler());
  }

  private class HandlerSupport<T> {
    private volatile Map<CefBrowser, List<T>> myMap;

    private synchronized void syncInitMap() {
      if (myMap == null) {
        myMap = Collections.synchronizedMap(new LinkedHashMap<>());
      }
    }

    private synchronized List<T> syncInitList(@NotNull CefBrowser browser, @NotNull Runnable onInit) {
      List<T> list = myMap.get(browser);
      if (list == null) {
        myMap.put(browser, list = Collections.synchronizedList(new LinkedList<>()));
        onInit.run();
      }
      return list;
    }

    private synchronized void syncRemoveFromMap(@NotNull List<T> list, @NotNull CefBrowser browser, @NotNull Runnable onClear) {
      if (list.isEmpty()) {
        myMap.remove(browser);
        onClear.run();
      }
    }

    public JBCefClient add(@NotNull T handler, @NotNull CefBrowser browser, @NotNull Runnable onInit) {
      if (myMap == null) {
        syncInitMap();
      }
      List<T> list = myMap.get(browser);
      if (list == null) {
        list = syncInitList(browser, onInit);
      }
      list.add(handler);
      return JBCefClient.this;
    }

    public void remove(@NotNull T handler, @NotNull CefBrowser browser, @NotNull Runnable onClear) {
      if (myMap != null) {
        List<T> list = myMap.get(browser);
        if (list != null) {
          list.remove(handler);
          if (list.isEmpty()) {
            syncRemoveFromMap(list, browser, onClear);
          }
        }
      }
    }

    public void clear() {
      if (myMap != null) {
        myMap.clear();
      }
    }

    @Nullable
    public List<T> get(@NotNull CefBrowser browser) {
      return myMap != null ? myMap.get(browser) : null;
    }

    @Nullable
    public <R> R handle(@NotNull CefBrowser browser, @NotNull HandlerCallable<T, R> callable) {
      List<T> list = get(browser);
      assert list != null;
      final Ref<R> lastResult = new Ref<>(null);
      list.forEach(handler -> lastResult.set(callable.handle(handler)));
      return lastResult.get();
    }

    @NotNull
    public <R> R handleNotNull(@NotNull CefBrowser browser, @NotNull HandlerCallableNotNull<T, R> callable) {
      return Objects.requireNonNull(handle(browser, callable));
    }


    public void handle(@NotNull CefBrowser browser, @NotNull HandlerRunnable<T> runnable) {
      List<T> list = get(browser);
      if (list == null) return;
      list.forEach(handler -> runnable.handle(handler));
    }
  }

  private interface HandlerCallable<T, R> {
    @Nullable
    R handle(T handler);
  }

  private interface HandlerCallableNotNull<T, R> extends HandlerCallable<T, R> {
    @Override
    @NotNull
    R handle(T handler);
  }

  private interface HandlerRunnable<T> {
    void handle(T handler);
  }
}
