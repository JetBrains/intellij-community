package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * Stands for an entity from external system domain. An external system might be 'maven', 'gradle', 'eclipse' etc, i.e. any
 * framework or platform which defines project structure model in its own terms. That module might be represented in terms
 * of this interface implementations then.
 * <p/>
 * It's assumed to be safe to use implementations of this interface at hash-based containers (i.e. they are expected to correctly 
 * override {@link #equals(Object)} and {@link #hashCode()}.
 * 
 * @author Denis Zhdanov
 * @since 8/12/11 12:50 PM
 */
public interface ExternalEntityData extends Serializable {

  @NotNull
  ProjectSystemId getOwner();
}
