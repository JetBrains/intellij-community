// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webcore.packaging;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.CatchingConsumer;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


public abstract class PackageManagementService {
  /**
   * Returns the list of URLs for all configured package repositories.
   *
   * @return list of URLs, or null if the repository management is not supported by this package management service.
   */
  public @Nullable List<String> getAllRepositories() {
    return null;
  }

  /**
   * An async version of {@link #getAllRepositories()}.
   */
  public void fetchAllRepositories(@NotNull CatchingConsumer<? super List<String>, ? super Exception> consumer) {
    consumer.consume(getAllRepositories());
  }

  /**
   * Returns true if the service supports managing repositories.
   */
  public boolean canManageRepositories() {
    return getAllRepositories() != null;
  }

  /**
   * Checks if the user can change the URL of the specified repository or remove it from the list.
   *
   * @param repositoryUrl the URL to check
   * @return true if can be modified, false otherwise.
   */
  public boolean canModifyRepository(String repositoryUrl) {
    return true;
  }

  public void addRepository(String repositoryUrl) {
  }

  public void removeRepository(String repositoryUrl) {
  }

  /**
   * @return a negative integer, if the first version is older than the second,
   *         zero, if the versions are equals,
   *         a positive integer, if the first version is newer than the second.
   */
  public int compareVersions(@NotNull String version1, @NotNull String version2) {
    return PackageVersionComparator.VERSION_COMPARATOR.compare(version1, version2);
  }

  /**
   * Returns the list of all packages in all configured repositories. Called in a background thread
   * and may initiate network connections. May return cached data.
   *
   * @return the list of all packages in all repositories
   */
  @RequiresBackgroundThread
  public abstract List<RepoPackage> getAllPackages() throws IOException;

  /**
   * Reloads the lists of packages for all configured repositories and returns the results. Called in a background thread
   * and may initiate network connections. May not return cached data.
   *
   * @return the list of all packages in all repositories
   */
  @RequiresBackgroundThread
  public abstract List<RepoPackage> reloadAllPackages() throws IOException;

  /**
   * Returns the cached list of all packages in all configured repositories, or an empty list if there is no cached information available.
   *
   * @return the list of all packages or an empty list.
   */
  public List<RepoPackage> getAllPackagesCached() {
    return Collections.emptyList();
  }

  /**
   * Returns true if the 'install to user' checkbox should be visible.
   */
  public boolean canInstallToUser() {
    return false;
  }

  /**
   * Returns the text of the 'install to user' checkbox.
   *
   * @return the text of the 'install to user' checkbox.
   */
  public @NlsContexts.Button String getInstallToUserText() {
    return "";
  }

  /**
   * Returns true if the 'install to user' checkbox should be initially selected.
   */
  public boolean isInstallToUserSelected() {
    return false;
  }

  /**
   * Called when the 'install to user' checkbox is checked or unchecked.
   */
  public void installToUserChanged(boolean newValue) {
  }

  /**
   * Returns the list of packages which are currently installed.
   *
   * @return the collection of currently installed packages.
   */
  public @NotNull List<? extends InstalledPackage> getInstalledPackagesList() throws ExecutionException {
    try {
      return new ArrayList<>(getInstalledPackages());
    }
    catch (IOException e) {
      throw new ExecutionException(e);
    }
  }

  /**
   * @deprecated Please use {@link #getInstalledPackagesList()} instead.
   */
  @Deprecated(since = "2020.2", forRemoval = true)
  public Collection<InstalledPackage> getInstalledPackages() throws IOException {
    throw new AbstractMethodError("The method is deprecated. Please use `getInstalledPackagesList`.");
  }

  /**
   * Installs the specified package. Called in the event dispatch thread; needs to take care of spawning a background task itself.
   *
   * @param repoPackage   the package to install
   * @param version       the version selected by the user, or null if the checkbox to install a specific version is not checked
   * @param forceUpgrade  if true, the latest version of the package is installed even if there is already an installed version
   * @param extraOptions  additional options entered by the user
   * @param listener      the listener that must be called to publish information about the installation progress
   * @param installToUser the state of the "install to user" checkbox (ignore if not applicable)
   */
  public abstract void installPackage(RepoPackage repoPackage, @Nullable String version, boolean forceUpgrade,
                                      @Nullable String extraOptions, Listener listener, boolean installToUser);

  public abstract void uninstallPackages(List<? extends InstalledPackage> installedPackages, Listener listener);

  public abstract void fetchPackageVersions(String packageName, CatchingConsumer<? super List<String>, ? super Exception> consumer);

  public abstract void fetchPackageDetails(String packageName, CatchingConsumer<? super @Nls String, ? super Exception> consumer);

  /**
   * @return identifier of this service for reported usage data (sent for JetBrains implementations only).
   *         Return null to avoid reporting any usage data.
   */
  public @Nullable @NonNls String getID() {
    return null;
  }

  public interface Listener {
    /**
     * Fired when the installation of the specified package is started.
     * Called from the caller thread.
     *
     * @param packageName the name of the package being installed.
     */
    void operationStarted(String packageName);

    /**
     * Fired when the installation of the specified package has been completed (successfully or unsuccessfully).
     * Called from the caller thread.
     *
     * @param packageName the name of the installed package.
     * @param errorDescription null if the package has been installed successfully, error message otherwise.
     */
    void operationFinished(String packageName, @Nullable ErrorDescription errorDescription);
  }

  public static class ErrorDescription {
    private final @NotNull @NlsContexts.DetailedDescription String myMessage;
    private final @Nullable String myCommand;
    private final @Nullable String myOutput;
    private final @Nullable @NlsContexts.DetailedDescription String mySolution;

    public static @Nullable ErrorDescription fromMessage(@Nullable @NlsContexts.DetailedDescription String message) {
      return message != null ? new ErrorDescription(message, null, null, null) : null;
    }

    public ErrorDescription(@NotNull @NlsContexts.DetailedDescription String message, @NlsSafe @Nullable String command, @NlsSafe @Nullable String output, @Nullable @NlsContexts.DetailedDescription String solution) {
      myMessage = message;
      myCommand = command;
      myOutput = output;
      mySolution = solution;
    }

    /**
     * The reason message that explains why the error has occurred.
     */
    public @NotNull @NlsContexts.DetailedDescription String getMessage() {
      return myMessage;
    }

    /**
     * The packaging command that has been executed, if it is meaningful to the user.
     */
    public @Nullable @NlsSafe String getCommand() {
      return myCommand;
    }

    /**
     * The output of the packaging command.
     */
    public @Nullable @NlsSafe String getOutput() {
      return myOutput;
    }

    /**
     * A possible solution of this packaging problem for the user.
     */
    public @Nullable @NlsContexts.DetailedDescription String getSolution() {
      return mySolution;
    }
  }
}
