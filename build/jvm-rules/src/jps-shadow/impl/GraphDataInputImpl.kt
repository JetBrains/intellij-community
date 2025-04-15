package org.jetbrains.jps.dependency.impl

import org.jetbrains.jps.dependency.ExternalizableGraphElement
import org.jetbrains.jps.dependency.GraphDataInput
import org.jetbrains.jps.dependency.storage.ClassRegistry

internal inline fun <T : ExternalizableGraphElement> doReadGraphElement(
  input: GraphDataInput,
  processLoadedGraphElement: (element: T) -> T,
): T {
  val classInfo = ClassRegistry.read(input)
  val constructor = if (classInfo.isFactored) {
    val factorData = input.readGraphElement<ExternalizableGraphElement>()
    classInfo.constructor.bindTo(factorData)
  }
  else {
    classInfo.constructor
  }
  @Suppress("UNCHECKED_CAST")
  return processLoadedGraphElement(constructor.invoke(input) as T)
}

internal inline fun <C : MutableCollection<in T>, T : ExternalizableGraphElement> doReadGraphElementCollection(
  input: GraphDataInput,
  result: C,
  processLoadedGraphElement: (element: T) -> T,
): C {
  val classInfo = ClassRegistry.read(input)
  val constructor = classInfo.constructor
  if (classInfo.isFactored) {
    repeat(input.readInt()) {
      val factorData = input.readGraphElement<ExternalizableGraphElement>()
      val constructor = constructor.bindTo(factorData)
      repeat(input.readInt()) {
        @Suppress("UNCHECKED_CAST")
        result.add(processLoadedGraphElement(constructor.invoke(input) as T))
      }
    }
  }
  else {
    repeat(input.readInt()) {
      @Suppress("UNCHECKED_CAST")
      result.add(processLoadedGraphElement(constructor.invoke(input) as T))
    }
  }
  return result
}