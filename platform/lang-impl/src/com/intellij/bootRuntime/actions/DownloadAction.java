// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.bootRuntime.actions;

import com.intellij.bootRuntime.bundles.Runtime;

import java.awt.event.ActionEvent;
import java.util.function.Supplier;

public class DownloadAction extends BinTrayDialogAction {
  public DownloadAction(Supplier<Runtime> selectedItemSupplier, Runnable updateCallback) {
    super("Download", selectedItemSupplier, updateCallback);
  }

  public void actionPerformed(ActionEvent actionEvent) {
    /*File downloadDirectoryFile = this.getDownloadDirectoryFile();
    if (!downloadDirectoryFile.exists()) {
      String link = "https://bintray.com/jetbrains/intellij-jdk/download_file?file_path=" + this.getFileName();
      this.runWithProgress("Downloading...", (progressIndicator) -> {
        progressIndicator.setIndeterminate(true);

        Response response;
        try {
          HttpConfigurable.getInstance().prepareURL(link);
          response = ClientBuilder.newClient(new ClientConfig()).target(link).request(new String[]{"application/octet-stream"}).get();
        } catch (Exception var18) {
          ApplicationManager.getApplication().invokeLater(() -> {
            Messages.showErrorDialog("It seems we have a problem with the network connection", "Network Issues");
          });
          return;
        }

        if (response.getStatus() == Status.OK.getStatusCode()) {
          try {
            InputStream is = response.readEntity(InputStream.class);
            Throwable var6 = null;

            try {
              BinTrayUtil.saveToFile(is, downloadDirectoryFile);
            } catch (Throwable var17) {
              var6 = var17;
              throw var17;
            } finally {
              if (is != null) {
                if (var6 != null) {
                  try {
                    is.close();
                  } catch (Throwable var16) {
                    var6.addSuppressed(var16);
                  }
                } else {
                  is.close();
                }
              }

            }
          } catch (IOException var20) {
            var20.printStackTrace();
          }
        } else {
          ApplicationManager.getApplication().invokeLater(() -> {
            Messages.showErrorDialog(response.getStatusInfo().toString(), "Network Issues");
          });
        }

        this.updateCallback.run();
      });
    }*/
  }
}
