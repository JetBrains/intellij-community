/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.ide;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

class OpenFileXmlRpcHandler {
  private static final Logger LOG = Logger.getInstance(OpenFileXmlRpcHandler.class);

  // XML-RPC interface method - keep the signature intact
  @SuppressWarnings("UnusedDeclaration")
  public boolean open(String path) {
    LOG.debug("open(" + path + ")");
    return doOpen(path, -1, -1);
  }

  // XML-RPC interface method - keep the signature intact
  @SuppressWarnings("UnusedDeclaration")
  public boolean openAndNavigate(String path, int line, int column) {
    LOG.debug("openAndNavigate(" + path + ", " + line + ", " + column + ")");
    return doOpen(path, line, column);
  }

  private static boolean doOpen(@NotNull String path, int line, int column) {
    OpenFileHttpService.OpenFileRequest request = new OpenFileHttpService.OpenFileRequest();
    request.file = path;
    request.line = line;
    request.column = column;
    request.focused = false;
    return HttpRequestHandler.EP_NAME.findExtension(OpenFileHttpService.class).openFile(request).getState() != Promise.State.REJECTED;
  }
}