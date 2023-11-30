// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects

import com.intellij.ide.trustedProjects.TrustedProjectsStateStorage.State
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.util.ThreeState
import com.intellij.util.containers.prefix.map.PrefixTreeMap
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
abstract class TrustedProjectsStateStorage<S: State>(state: S) : SerializablePersistentStateComponent<S>(state) {

  interface State {

    val trustedState: PrefixTreeMap<Path, Boolean>
  }

  open fun getProjectPathTrustedState(path: Path): ThreeState {
    val closestAncestorState = state.trustedState.getAncestorEntries(path)
      .maxByOrNull { it.key.nameCount }
    if (closestAncestorState != null) {
      return ThreeState.fromBoolean(closestAncestorState.value)
    }
    return ThreeState.UNSURE
  }

  open fun getProjectTrustedState(locatedProject: TrustedProjectsLocator.LocatedProject): ThreeState {
    return locatedProject.projectRoots.asSequence()
      .map { getProjectPathTrustedState(it) }
      .fold(ThreeState.YES) { acc, it ->
        when {
          acc == ThreeState.UNSURE -> ThreeState.UNSURE
          it == ThreeState.UNSURE -> ThreeState.UNSURE
          acc == ThreeState.NO -> ThreeState.NO
          it == ThreeState.NO -> ThreeState.NO
          else -> ThreeState.YES
        }
      }
  }
}