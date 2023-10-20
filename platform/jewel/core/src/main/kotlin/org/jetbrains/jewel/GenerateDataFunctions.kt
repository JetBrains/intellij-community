package org.jetbrains.jewel

/**
 * Instructs the Poko compiler plugin to generate equals, hashcode,
 * and toString functions for the class it's attached to.
 *
 * See https://github.com/JetBrains/jewel/issues/83
 */
@Target(AnnotationTarget.CLASS)
annotation class GenerateDataFunctions
