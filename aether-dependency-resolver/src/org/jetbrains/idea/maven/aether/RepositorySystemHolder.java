// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.aether;

import org.apache.maven.model.Activation;
import org.apache.maven.model.Profile;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.profile.ProfileActivationContext;
import org.apache.maven.model.profile.activation.ProfileActivator;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.internal.impl.TrackingFileManager;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.jetbrains.annotations.NotNull;

final class RepositorySystemHolder {

  private static final RepositorySystem ourSystem = getRepositorySystem();

  public static @NotNull RepositorySystem getInstance() {
    return ourSystem;
  }

  private static @NotNull RepositorySystem getRepositorySystem() {
    DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
    locator.setServices(TrackingFileManager.class, new NioTrackingFileManager());
    locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
    locator.addService(TransporterFactory.class, FileTransporterFactory.class);
    locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
    locator.setServices(ModelBuilder.class, new DefaultModelBuilderFactory() {
      @Override
      public ProfileActivator[] newProfileActivators() {
        // allow pom profiles to make dependency resolution deterministic and predictable:
        // consider all possible dependencies the artifact can potentially have.
        return new ProfileActivator[] {new ProfileActivatorProxy(super.newProfileActivators())};
      }
    }.newInstance());
    locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
      @Override
      public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
        if (exception != null) {
          throw new RuntimeException(exception);
        }
      }
    });
    return locator.getService(RepositorySystem.class);
  }

  // Force certain activation kinds to be always active in order to include such dependencies in dependency resolution process
  // Currently JDK activations are always enabled for the purpose of transitive artifact discovery
  private static class ProfileActivatorProxy implements ProfileActivator {

    private final ProfileActivator[] myDelegates;

    ProfileActivatorProxy(ProfileActivator[] delegates) {
      myDelegates = delegates;
    }

    private static boolean isForceActivation(Profile profile) {
      Activation activation = profile.getActivation();
      return activation != null && activation.getJdk() != null;
    }

    @Override
    public boolean isActive(Profile profile, ProfileActivationContext context, ModelProblemCollector problems) {
      if (isForceActivation(profile)) {
        return true;
      }
      Boolean active = null;
      for (ProfileActivator delegate : myDelegates) {
        if (delegate.presentInConfig(profile, context, problems)) {
          boolean activeValue = delegate.isActive(profile, context, problems);
          active = active == null? activeValue : active && activeValue;
        }
      }
      return Boolean.TRUE.equals(active);
    }

    @Override
    public boolean presentInConfig(Profile profile, ProfileActivationContext context, ModelProblemCollector problems) {
      if (isForceActivation(profile)) {
        return true;
      }
      for (ProfileActivator delegate : myDelegates) {
        if (delegate.presentInConfig(profile, context, problems)) {
          return true;
        }
      }
      return false;
    }
  }
}
