// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.SmartList;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Eugene Zhuravlev
 */
@State(name = "RemoteRepositoriesConfiguration", storages = @Storage("jarRepositories.xml"))
public class RemoteRepositoriesConfiguration implements PersistentStateComponent<RemoteRepositoriesConfiguration.State>, Disposable {
  private final List<RemoteRepositoryDescription> myRepositories = new SmartList<>();

  public RemoteRepositoriesConfiguration() {
    this(RemoteRepositoryDescription.DEFAULT_REPOSITORIES);
  }

  public RemoteRepositoriesConfiguration(Collection<RemoteRepositoryDescription> repos) {
    myRepositories.addAll(repos);
  }

  @NotNull
  public static RemoteRepositoriesConfiguration getInstance(Project project) {
    return project.getService(RemoteRepositoriesConfiguration.class);
  }

  @NotNull
  public List<RemoteRepositoryDescription> getRepositories() {
    return Collections.unmodifiableList(myRepositories);
  }

  public void resetToDefault() {
    setRepositories(Collections.emptyList());
  }

  public void setRepositories(@NotNull List<RemoteRepositoryDescription> repos) {
    myRepositories.clear();
    myRepositories.addAll(repos.isEmpty() ? RemoteRepositoryDescription.DEFAULT_REPOSITORIES : repos);
  }

  @Nullable
  @Override
  public RemoteRepositoriesConfiguration.State getState() {
    return new State(myRepositories);
  }

  @Override
  public void loadState(@NotNull RemoteRepositoriesConfiguration.State state) {
    final List<RemoteRepositoryDescription> loaded = new SmartList<>();
    for (State.Repo repo : state.data) {
      loaded.add(new RemoteRepositoryDescription(repo.id, repo.name, repo.url));
    }
    setRepositories(loaded);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RemoteRepositoriesConfiguration that = (RemoteRepositoriesConfiguration)o;

    if (!myRepositories.equals(that.myRepositories)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myRepositories.hashCode();
  }

  static class State {
    @Tag("remote-repository")
    static class Repo {
      public String id;
      public String name;
      public String url;

      // needed for PersistentStateComponent
      @SuppressWarnings("unused")
      Repo() {
      }

      Repo(@NotNull String id, @NotNull String name, @NotNull String url) {
        this.id = id;
        this.name = name;
        this.url = url;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Repo repo = (Repo)o;

        return Objects.equals(id, repo.id) && Objects.equals(name, repo.name) && Objects.equals(url, repo.url);
      }

      @Override
      public int hashCode() {
        return Objects.hash(id, name, url);
      }
    }

    @NotNull
    @Property(surroundWithTag = false)
    @XCollection
    public final List<Repo> data = new SmartList<>();

    // needed for PersistentStateComponent
    @SuppressWarnings("unused")
    State() {
      this(RemoteRepositoryDescription.DEFAULT_REPOSITORIES);
    }

    State(List<RemoteRepositoryDescription> repos) {
      for (RemoteRepositoryDescription repository : repos) {
        data.add(new Repo(repository.getId(), repository.getName(), repository.getUrl()));
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      State state = (State)o;

      if (!data.equals(state.data)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return data.hashCode();
    }
  }

  @Override
  public void dispose() {
  }
}
