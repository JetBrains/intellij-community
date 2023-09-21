// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.ConfigurationException;
import org.jetbrains.jps.dependency.NodeSerializer;
import org.jetbrains.jps.dependency.NodeSerializerRegistry;
import org.jetbrains.jps.dependency.SerializableGraphElement;
import org.jetbrains.jps.dependency.impl.serializer.FileSourceNodeSerializerImpl;
import org.jetbrains.jps.dependency.impl.serializer.JvmClassNodeSerializerImpl;
import org.jetbrains.jps.dependency.impl.serializer.JvmModuleNodeSerializerImpl;
import org.jetbrains.jps.dependency.impl.serializer.StringReferenceIDNodeSerializerImpl;

import java.util.*;
import java.util.function.Function;

public class SerializerRegistryImpl implements NodeSerializerRegistry {
  private final Function<SerializableGraphElement, NodeSerializer> mySerializerSelector;
  private final Function<Integer, NodeSerializer> mySerializerIDSelector;

  private static final NodeSerializerRegistry INSTANCE =
    new SerializerRegistryImpl(
      Arrays.asList(new FileSourceNodeSerializerImpl(), new StringReferenceIDNodeSerializerImpl(), new JvmClassNodeSerializerImpl(), new JvmModuleNodeSerializerImpl()));

  public static NodeSerializerRegistry getInstance() {
    return INSTANCE;
  }

  private SerializerRegistryImpl(@NotNull Collection<NodeSerializer> serializers) {
    if (serializers.isEmpty()) {
      throw new ConfigurationException("Should be at least one NodeSerializer registered");
    }
    mySerializerSelector = createSerializerSelector(serializers);
    mySerializerIDSelector = new Function<>() {
      private final Map<Integer, NodeSerializer> myCache = new HashMap<>();
      {
        for (NodeSerializer serializer : serializers) {
          myCache.put(serializer.getId(), serializer);
        }
      }

      @Override
      public NodeSerializer apply(Integer serializerId) {
        NodeSerializer serializer = myCache.get(serializerId);
        if (serializer == null) {
          throw new ConfigurationException("No serializers found with ID= " + serializerId);
        }
        return serializer;
      }
    };
  }

  @Override
  public NodeSerializer getSerializer(SerializableGraphElement element) throws ConfigurationException {
    return mySerializerSelector.apply(element);
  }

  @Override
  public NodeSerializer getSerializer(int serializerId) throws ConfigurationException {
    return mySerializerIDSelector.apply(serializerId);
  }

  private static Function<SerializableGraphElement, NodeSerializer> createSerializerSelector(Collection<NodeSerializer> serializers) {
    if (serializers.size() == 1) {
      NodeSerializer serializer = serializers.iterator().next();
      return elem -> {
        if (!serializer.isSupported(elem)) {
          throw new ConfigurationException("The only available NodeSerializer does not support element " + elem);
        }
        return serializer;
      };
    }

    return new Function<>() {

      private final Map<Class<? extends SerializableGraphElement>, NodeSerializer> myCache = new HashMap<>();

      @Override
      public NodeSerializer apply(SerializableGraphElement elem) {
        Class<? extends SerializableGraphElement> implCls = elem.getClass();
        NodeSerializer serializer = myCache.get(implCls);
        if (serializer == null) {
          for (NodeSerializer nodeSerializer : serializers) {
            if (nodeSerializer.isSupported(elem)) {
              myCache.put(implCls, serializer = nodeSerializer);
              break;
            }
          }
        }
        if (serializer == null) {
          throw new ConfigurationException("No compatible serializers found for element " + elem);
        }
        return serializer;
      }
    };
  }

}
