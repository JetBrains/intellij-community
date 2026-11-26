// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.IconIdentifier
import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.ImageResourceLocation
import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.design.IconAlign
import com.intellij.platform.icons.design.IconHorizontalAlign
import com.intellij.platform.icons.design.IconMargin
import com.intellij.platform.icons.design.IconUnit
import com.intellij.platform.icons.design.IconVerticalAlign
import com.intellij.platform.icons.design.Shape
import com.intellij.platform.icons.design.dp
import com.intellij.platform.icons.design.px
import com.intellij.platform.icons.design.sRGB
import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.impl.DefaultIconManager
import com.intellij.platform.icons.impl.design.DefaultIconAlign
import com.intellij.platform.icons.impl.modifiers.ApplyableIconModifier
import com.intellij.platform.icons.layers.IconLayer
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import com.intellij.platform.icons.modifiers.align
import com.intellij.platform.icons.modifiers.alpha
import com.intellij.platform.icons.modifiers.cutoutMargin
import com.intellij.platform.icons.modifiers.margin
import com.intellij.platform.icons.modifiers.scale
import com.intellij.platform.icons.modifiers.sizeVariants
import com.intellij.platform.icons.modifiers.stroke
import com.intellij.platform.icons.scale.IconScale
import com.intellij.platform.icons.scale.factor
import com.intellij.platform.icons.scale.fitArea
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.relativeTo
import kotlin.reflect.KClass

/**
 * Serialization of icon descriptors, for example to send them to the frontend in remote development. A layer holds its
 * modifier as an [IconModifier], so each serializable modifier must be registered polymorphically.
 */
@TestApplication
class IconSerializationTest {
  private val classLoader = IconSerializationTest::class.java.classLoader

  @BeforeEach
  fun activateIconManager() {
    IntelliJIconManager.activate()
  }

  @Test
  fun `a descriptor with all kinds of modifiers survives serialization`() {
    val modifier = IconModifier
      .sizeVariants(SizeVariantPolicy.Fitting)
      .scale(fitArea(18.dp, 18.dp, upscale = false))
      .alpha(0.5f)
      .margin(1.dp, 2.px, 3.dp, 4.px)
      .cutoutMargin(2.dp)
      .stroke(sRGB("#FF0000"))
      .align(DefaultIconAlign(IconVerticalAlign.Center, IconHorizontalAlign.Right))
    val descriptor = iconDescriptor {
      image("iconsTest/variant/test.svg", classLoader, modifier)
      icon(iconDescriptor { image("iconsTest/variant/wide.svg", classLoader) }, IconModifier.scale(factor(1.5f)))
    }
    val serializer = PolymorphicSerializer(IconDescriptor::class)

    val text = json().encodeToString(serializer, descriptor)

    assertEquals(descriptor, json().decodeFromString(serializer, text), text)
  }

  /**
   * Fails when a serializable class of the icons API implementation has no polymorphic registration for a type it
   * implements. A property of that type, such as the modifier of a layer, then cannot be serialized.
   */
  @OptIn(InternalSerializationApi::class)
  @Test
  fun `each serializable implementation is registered polymorphically`() {
    val module = (IconManager.getInstance() as DefaultIconManager).getSerializersModule()
    val classes = (implementationClasses(DefaultIconManager::class.java) + implementationClasses(IntelliJIconManager::class.java))
      .filter { it.isAnnotationPresent(Serializable::class.java) && !it.isInterface && !Modifier.isAbstract(it.modifiers) }
    assertTrue(classes.size > 20, "the scan must find the serializable classes, found: $classes")
    val missing = buildList {
      for (klass in classes) {
        val serialName = klass.kotlin.serializer().descriptor.serialName
        for (base in POLYMORPHIC_BASES) {
          if (!base.java.isAssignableFrom(klass)) continue
          @Suppress("UNCHECKED_CAST")
          if (module.getPolymorphic(base as KClass<Any>, serializedClassName = serialName) == null) add("${klass.name} as ${base.simpleName}")
        }
      }
    }
    assertEquals(emptyList<String>(), missing)
  }

  private fun json(): Json = Json { serializersModule = (IconManager.getInstance() as DefaultIconManager).getSerializersModule() }

  /** The classes in the package `com.intellij.platform.icons.impl` of the module of [anchor]. */
  private fun implementationClasses(anchor: Class<*>): List<Class<*>> {
    // the code source can be unknown, so the location comes from the URL of the class file
    val classFile = anchor.name.replace('.', '/') + ".class"
    val url = anchor.classLoader.getResource(classFile)!!.toExternalForm()
    val names = if (url.startsWith("jar:")) {
      val location = Path.of(URI(url.removePrefix("jar:").substringBefore("!/")))
      JarFile(location.toFile()).use { jar -> jar.entries().asSequence().map { it.name }.filter { it.endsWith(".class") }.toList() }
    }
    else {
      val location = Path.of(URI(url.removeSuffix(classFile)))
      check(location.isDirectory()) { "Unexpected location of $classFile: $url" }
      Files.walk(location).use { paths ->
        paths.filter { it.extension == "class" }.map { it.relativeTo(location).invariantSeparatorsPathString }.toList()
      }
    }
    return names
      .filter { it.startsWith("com/intellij/platform/icons/impl/") }
      .map { Class.forName(it.removeSuffix(".class").replace('/', '.'), false, anchor.classLoader) }
  }

  private companion object {
    val POLYMORPHIC_BASES: List<KClass<*>> = listOf(
      IconDescriptor::class, IconLayer::class, IconModifier::class, ApplyableIconModifier::class, ImageResourceLocation::class,
      IconIdentifier::class, IconScale::class, IconUnit::class, Color::class, Shape::class, IconAlign::class, IconMargin::class,
    )
  }
}
