// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper;

import com.intellij.ide.XmlRpcServer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import externalApp.ExternalApp;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.BuiltInServerManager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <p>The provider of external application scripts called by Git when a remote operation needs communication with the user.</p>
 * <p>
 *   Usage:
 *   <ol>
 *     <li>Get the script from {@link #getScriptPath(GitExecutable, boolean)}.</li>
 *     <li>Set up proper environment variable
 *         (e.g. {@code GIT_SSH} for SSH connections, or {@code GIT_ASKPASS} for HTTP) pointing to the script.</li>
 *     <li>{@link #registerHandler(Object) Register} the handler of Git requests.</li>
 *     <li>Call Git operation.</li>
 *     <li>If the operation requires user interaction, the registered handler is called via XML RPC protocol.
 *         It can show a dialog in the GUI and return the answer via XML RPC to the external application, that further provides
 *         this value to the Git process.</li>
 *     <li>{@link #unregisterHandler(UUID)} Unregister} the handler after operation has completed.</li>
 *   </ol>
 * </p>
 */
public abstract class GitXmlRpcHandlerService<T> implements Disposable {
  private static final Logger LOG = Logger.getInstance(GitXmlRpcHandlerService.class);

  @NotNull private final @NonNls String myScriptTempFilePrefix;
  @NotNull private final @NonNls String myHandlerName;
  @NotNull private final Class<? extends ExternalApp> myScriptMainClass;

  @NotNull private final Map<@NonNls String, File> myScriptPaths = new HashMap<>();
  @NotNull private final Object SCRIPT_FILE_LOCK = new Object();

  @NotNull private final Map<UUID, T> handlers = new HashMap<>();
  @NotNull private final Object HANDLERS_LOCK = new Object();

  /**
   * @param handlerName Returns the name of the handler to be used by XML RPC client to call remote methods of a proper object.
   * @param aClass      Main class of the external application invoked by Git,
   *                    which is able to handle its requests and pass to the main IDEA instance.
   */
  protected GitXmlRpcHandlerService(@NotNull @NonNls String prefix,
                                    @NotNull @NonNls String handlerName,
                                    @NotNull Class<? extends ExternalApp> aClass) {
    myScriptTempFilePrefix = prefix;
    myHandlerName = handlerName;
    myScriptMainClass = aClass;
  }

  /**
   * @return the port number for XML RCP
   */
  public int getXmlRcpPort() {
    return BuiltInServerManager.getInstance().waitForStart().getPort();
  }

  /**
   * Get file to the script service
   *
   * @return path to the script
   * @throws IOException if script cannot be generated
   */
  @NotNull
  public File getScriptPath(@NotNull String scriptId, boolean useBatchFile, @Nullable ScriptGenerator.CustomScriptCommandLineBuilder customCmdBuilder) throws IOException {
    synchronized (SCRIPT_FILE_LOCK) {
      String id = scriptId + (useBatchFile ? "-bat" : ""); //NON-NLS
      File scriptPath = myScriptPaths.get(id);
      if (scriptPath == null || !scriptPath.exists()) {
        ScriptGenerator generator = new ScriptGenerator(myScriptTempFilePrefix + "-" + scriptId, myScriptMainClass);
        scriptPath = generator.generate(useBatchFile, customCmdBuilder);
        myScriptPaths.put(id, scriptPath);
      }
      return scriptPath;
    }
  }

  /**
   * Register handler. Note that handlers must be unregistered using {@link #unregisterHandler(UUID)}.
   *
   * @param handler a handler to register
   * @return an identifier to pass to the environment variable
   */
  @NotNull
  public UUID registerHandler(@NotNull T handler) {
    synchronized (HANDLERS_LOCK) {
      XmlRpcServer xmlRpcServer = XmlRpcServer.SERVICE.getInstance();
      if (!xmlRpcServer.hasHandler(myHandlerName)) {
        xmlRpcServer.addHandler(myHandlerName, createRpcRequestHandlerDelegate());
      }

      final UUID key = UUID.randomUUID();
      handlers.put(key, handler);
      return key;
    }
  }

  @Override
  public void dispose() {
    XmlRpcServer xmlRpcServer = ApplicationManager.getApplication().getServiceIfCreated(XmlRpcServer.class);
    if (xmlRpcServer != null) {
      xmlRpcServer.removeHandler(myHandlerName);
    }
  }

  /**
   * Creates an implementation of the xml rpc handler, which methods will be called from the external application.
   * This method should just delegate the call to the specific handler of type {@link T}, which can be achieved by {@link #getHandler(UUID)}.
   * @return New instance of the xml rpc handler delegate.
   */
  @NotNull
  protected abstract Object createRpcRequestHandlerDelegate();

  /**
   * Get handler for the key
   *
   * @param key the key to use
   * @return the registered handler
   */
  @NotNull
  protected T getHandler(UUID key) {
    synchronized (HANDLERS_LOCK) {
      T rc = handlers.get(key);
      if (rc == null) {
        throw new IllegalStateException("No handler for the key " + key);
      }
      return rc;
    }
  }

  /**
   * Unregister handler by the key
   *
   * @param key the key to unregister
   */
  public void unregisterHandler(UUID key) {
    synchronized (HANDLERS_LOCK) {
      if (handlers.remove(key) == null) {
        LOG.error("The handler " + key + " is not registered");
      }
    }
  }

}
