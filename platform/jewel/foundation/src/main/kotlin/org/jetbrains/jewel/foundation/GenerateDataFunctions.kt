package org.jetbrains.jewel.foundation

/**
 * Instructs detekt to error out if equality members of the annotated class are missing or do not contain all properties
 * from the constructor.
 *
 * Previous behavior: Instructs the Poko compiler plugin to generate equals, hashcode, and toString functions for the
 * class it's attached to.
 *
 * See [this issue](https://github.com/JetBrains/jewel/issues/83) for details.
 */
@Target(AnnotationTarget.CLASS) public annotation class GenerateDataFunctions
