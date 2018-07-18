/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.remoteServer.impl.module;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.runtime.Deployment;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnector;
import com.intellij.remoteServer.runtime.deployment.ServerRuntimeInstance;
import com.intellij.remoteServer.util.*;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;


public abstract class CloudSourceApplicationConfigurable<
  SC extends CloudConfigurationBase,
  DC extends CloudDeploymentNameConfiguration,
  SR extends CloudMultiSourceServerRuntimeInstance<DC, ?, ?, ?>,
  AC extends CloudApplicationConfiguration> extends CloudApplicationConfigurable {

  private final Project myProject;
  private final Disposable myParentDisposable;

  private DelayedRunner myRunner;

  private RemoteServer<?> myAccount;

  public CloudSourceApplicationConfigurable(@Nullable Project project, Disposable parentDisposable) {
    myProject = project;
    myParentDisposable = parentDisposable;
  }

  @Override
  public void setAccount(RemoteServer<?> account) {
    myAccount = account;
    clearCloudData();
  }

  protected RemoteServer<SC> getAccount() {
    return (RemoteServer<SC>)myAccount;
  }

  @Override
  public JComponent getComponent() {
    JComponent result = getMainPanel();
    if (myRunner == null) {
      myRunner = new DelayedRunner(result) {

        private RemoteServer<?> myPreviousAccount;

        @Override
        protected boolean wasChanged() {
          boolean result = myPreviousAccount != myAccount;
          if (result) {
            myPreviousAccount = myAccount;
          }
          return result;
        }

        @Override
        protected void run() {
          loadCloudData();
        }
      };
      Disposer.register(myParentDisposable, myRunner);
    }
    return result;
  }

  protected void clearCloudData() {
    getExistingComboBox().removeAllItems();
  }

  protected void loadCloudData() {
    new ConnectionTask<Collection<Deployment>>("Loading existing applications list") {

      @Override
      protected void run(final ServerConnection<DC> connection,
                         final Semaphore semaphore,
                         final AtomicReference<Collection<Deployment>> result) {
        connection.connectIfNeeded(new ServerConnector.ConnectionCallback<DC>() {

          @Override
          public void connected(@NotNull ServerRuntimeInstance<DC> serverRuntimeInstance) {
            connection.computeDeployments(() -> {
              result.set(connection.getDeployments());
              semaphore.up();
              UIUtil.invokeLaterIfNeeded(() -> {
                if (!Disposer.isDisposed(myParentDisposable)) {
                  setupExistingApplications(result.get());
                }
              });
            });
          }

          @Override
          public void errorOccurred(@NotNull String errorMessage) {
            runtimeErrorOccurred(errorMessage);
            semaphore.up();
          }
        });
      }

      @Override
      protected Collection<Deployment> run(SR serverRuntimeInstance) throws ServerRuntimeException {
        return null;
      }
    }.performAsync();
  }

  private void setupExistingApplications(Collection<Deployment> deployments) {
    JComboBox existingComboBox = getExistingComboBox();
    existingComboBox.removeAllItems();
    for (Deployment deployment : deployments) {
      existingComboBox.addItem(deployment.getPresentableName());
    }
  }

  protected Project getProject() {
    return myProject;
  }

  protected abstract JComboBox getExistingComboBox();

  protected abstract JComponent getMainPanel();

  @Override
  public abstract AC createConfiguration();

  protected abstract class ConnectionTask<T> extends CloudConnectionTask<T, SC, DC, SR> {

    public ConnectionTask(String title) {
      super(myProject, title, CloudSourceApplicationConfigurable.this.getAccount());
    }
  }
}
