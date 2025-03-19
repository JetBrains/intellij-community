/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic.hprof.classstore

import com.intellij.diagnostic.hprof.parser.Type
import com.intellij.diagnostic.hprof.util.IDMapper
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ClassStore(private val classes: Long2ObjectMap<ClassDefinition>) {
  private val stringToClassDefinition = HashMap<String, ClassDefinition>()
  private val classDefinitionToShortPrettyName = HashSet<ClassDefinition>()

  val softReferenceClass: ClassDefinition
  val weakReferenceClass: ClassDefinition

  val classClass: ClassDefinition

  private val primitiveArrayToClassDefinition = HashMap<Type, ClassDefinition>()

  init {
    fun getClashedNameWithIndex(classDefinition: ClassDefinition, index: Int): String {
      if (classDefinition.name.endsWith(';')) {
        return "${classDefinition.name.removeSuffix(";")}!$index;"
      }
      else {
        return "${classDefinition.name}!$index"
      }
    }

    val clashedClassNames = HashSet<String>()
    for (classDefinition in classes.values) {
      val className = classDefinition.name
      var clashed = false
      if (clashedClassNames.contains(className)) {
        clashed = true
      }
      else {
        val clashedClass = stringToClassDefinition.remove(className)
        // If there is more than one class with this name, rename first class too.
        if (clashedClass != null) {
          clashed = true
          val newDefinition = clashedClass.copyWithName(getClashedNameWithIndex(clashedClass, 1))
          stringToClassDefinition[newDefinition.name] = newDefinition
          classes.put(clashedClass.id, newDefinition)
          clashedClassNames.add(className)
        }
      }
      if (clashed) {
        var i = 2
        var newName: String
        do {
          newName = getClashedNameWithIndex(classDefinition, i)
          i++
        }
        while (stringToClassDefinition.containsKey(newName))

        val newClassDefinition = classDefinition.copyWithName(newName)
        stringToClassDefinition[newName] = newClassDefinition
        classes.put(classDefinition.id, newClassDefinition)
      }
      else {
        stringToClassDefinition[classDefinition.name] = classDefinition
      }
    }
    clashedClassNames.forEach { assert(!stringToClassDefinition.contains(it)) }

    // Every heap dump should have definitions of Soft/WeakReference and java.lang.Class
    softReferenceClass = stringToClassDefinition["java.lang.ref.SoftReference"]!!
    weakReferenceClass = stringToClassDefinition["java.lang.ref.WeakReference"]!!
    classClass = stringToClassDefinition["java.lang.Class"]!!

    Type.values().forEach { type ->
      if (type == Type.OBJECT) {
        return@forEach
      }
      stringToClassDefinition[type.getClassNameOfPrimitiveArray()]?.let { classDefinition ->
        primitiveArrayToClassDefinition.put(type, classDefinition)
      }
    }

    val shortNameToClassDefinition = HashMap<String, ClassDefinition?>()
    for (classDefinition in classes.values) {
      if (classDefinition.name.contains('$')) {
        continue
      }

      val prettyName = classDefinition.prettyName
      val shortPrettyName = prettyName.substringAfterLast('.')
      if (shortNameToClassDefinition.containsKey(shortPrettyName)) {
        val prevClassDefinition = shortNameToClassDefinition[shortPrettyName]
        if (prevClassDefinition != null) {
          shortNameToClassDefinition[shortPrettyName] = null
        }
      }
      else {
        shortNameToClassDefinition[shortPrettyName] = classDefinition
      }
    }
    shortNameToClassDefinition.forEach { (_, classDef) ->
      if (classDef == null) return@forEach
      classDefinitionToShortPrettyName.add(classDef)
    }
  }

  operator fun get(id: Int): ClassDefinition = classes.get(id.toLong())!!

  operator fun get(id: Long): ClassDefinition = classes.get(id)!!

  operator fun get(name: String): ClassDefinition = stringToClassDefinition.get(name)!!

  fun getClassIfExists(name: String): ClassDefinition? = stringToClassDefinition[name]

  fun containsClass(name: String): Boolean = stringToClassDefinition.containsKey(name)

  fun getClassForPrimitiveArray(t: Type): ClassDefinition? = primitiveArrayToClassDefinition[t]

  fun size(): Int = classes.size

  fun isSoftOrWeakReferenceClass(classDefinition: ClassDefinition): Boolean {
    return classDefinition == softReferenceClass || classDefinition == weakReferenceClass
  }

  fun forEachClass(func: (ClassDefinition) -> Unit) {
    for (classDefinition in classes.values) {
      func(classDefinition)
    }
  }

  fun createStoreWithRemappedIDs(idMapper: IDMapper): ClassStore {
    fun map(id: Long): Long = idMapper.getID(id)
    val newClasses = Long2ObjectOpenHashMap<ClassDefinition>()
    for (classDefinition in classes.values) {
      newClasses.put(map(classDefinition.id), classDefinition.copyWithRemappedIDs(idMapper))
    }
    return ClassStore(newClasses)
  }

  fun getShortPrettyNameForClass(classDefinition: ClassDefinition): String {
    if (classDefinition.name.contains('$')) {
      val outerClass = stringToClassDefinition[classDefinition.name.substringBefore('$')]
      if (outerClass != null) {
        if (classDefinitionToShortPrettyName.contains(outerClass)) {
          return classDefinition.prettyName.substringAfterLast('.')
        }
      }
    }
    else if (classDefinitionToShortPrettyName.contains(classDefinition)) {
      return classDefinition.prettyName.substringAfterLast('.')
    }
    return classDefinition.prettyName
  }
}
