/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.jarRepository;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
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

/**
 * @author Eugene Zhuravlev
 */
@State(name = "RemoteRepositoriesConfiguration", storages = @Storage("jarRepositories.xml"))
public class RemoteRepositoriesConfiguration implements PersistentStateComponent<RemoteRepositoriesConfiguration.State> {
  private final List<RemoteRepositoryDescription> myRepositories = new SmartList<>();

  public RemoteRepositoriesConfiguration() {
    this(RemoteRepositoryDescription.DEFAULT_REPOSITORIES);
  }

  public RemoteRepositoriesConfiguration(Collection<RemoteRepositoryDescription> repos) {
    myRepositories.addAll(repos);
  }

  @NotNull
  public static RemoteRepositoriesConfiguration getInstance(Project project) {
    return ServiceManager.getService(project, RemoteRepositoriesConfiguration.class);
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
    myRepositories.addAll(repos.isEmpty()? RemoteRepositoryDescription.DEFAULT_REPOSITORIES : repos);
  }

  @Nullable
  @Override
  public RemoteRepositoriesConfiguration.State getState() {
    return new State(myRepositories);
  }

  @Override
  public void loadState(RemoteRepositoriesConfiguration.State state) {
    final List<RemoteRepositoryDescription> loaded = new SmartList<>();
    if (state.data != null) {
      for (State.Repo repo : state.data) {
        loaded.add(new RemoteRepositoryDescription(repo.id, repo.name, repo.url));
      }
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
     static class Repo{
       public String id;
       public String name;
       public String url;

       public Repo() {
       }

       public Repo(String id, String name, String url) {
         this.id = id;
         this.name = name;
         this.url = url;
       }

       @Override
       public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;

         Repo repo = (Repo)o;

         if (id != null ? !id.equals(repo.id) : repo.id != null) return false;
         if (name != null ? !name.equals(repo.name) : repo.name != null) return false;
         if (url != null ? !url.equals(repo.url) : repo.url != null) return false;

         return true;
       }

       @Override
       public int hashCode() {
         int result = id != null ? id.hashCode() : 0;
         result = 31 * result + (name != null ? name.hashCode() : 0);
         result = 31 * result + (url != null ? url.hashCode() : 0);
         return result;
       }
     }

     @NotNull
     @Property(surroundWithTag = false)
     @XCollection
     public final List<Repo> data = new SmartList<>();

     public State() {
       this(RemoteRepositoryDescription.DEFAULT_REPOSITORIES);
     }

     public State(List<RemoteRepositoryDescription> repos) {
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
}
