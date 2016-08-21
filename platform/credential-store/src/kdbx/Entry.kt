/*
 * Copyright 2015 Jo Rabin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.credentialStore.kdbx

import java.util.*

/**
 * Interface for a Database Entry. Database entries provide support
 * at least for the Standard Properties of
 *
 *  * Title
 *  * Username
 *  * Password
 *  * URL
 *  * Notes
 *

 *
 * Entries have a UUID and have an Icon, which are not properties.

 *
 * Support for additional textual properties may be provided
 * by a database implementation.

 *
 * At some point support for binary properties may be added
 * to this interface

 *
 * Entries provide support for tracking when they are used.
 * At some point support for accessing a history of modifications
 * may be added to this interface

 * @author Jo
 */
interface Entry {

  /**
   * Interface to implement when using the [.match]
   * method
   */
  interface Matcher {
    fun matches(entry: Entry): Boolean
  }

  /**
   * Returns an XPath-like representation of this
   * entry's ancestor groups and the title of this entry.
   */
  val path: String

  /**
   * Gets the value of a property.

   *
   * All implementations of Entry are required to support reading and writing of
   * [.STANDARD_PROPERTY_NAMES].
   * @param name the name of the property to get
   * *
   * @return a value or null if the property is not known, or if setting of arbitrary properties is not supported
   */
  fun getProperty(name: String): String?

  /**
   * Sets the value of a property.

   *
   * Other than the [.STANDARD_PROPERTY_NAMES] support for this methd is optional.

   * @param name the name of the property to set
   * *
   * @param value the value to set it to
   * *
   * @throws UnsupportedOperationException if the name is not one of the standard properties and
   * * non-standard properties are not supported.
   */
  fun setProperty(name: String, value: String?)

  /**
   * Returns a list of property names known to the entry.

   *
   * All implementations of Entry are required to support reading and writing of
   * [.STANDARD_PROPERTY_NAMES].
   * @return a list that is modifiable by the caller without affecting the Entry.
   */
  val propertyNames: List<String>

  val parent: KdbxGroup?

  /**
   * Get the UUID of this entry. Databases (like KDB) that do not natively support
   * UUIDs must provide a surrogate here.

   * @return a UUID
   */
  val uuid: UUID
  var userName: String?
  var password: String?

  /**
   * Gets the URL for this entry.

   *
   * Implementations should Touch LastAccessedTime when this method is called.

   * @return a string representation of a URL
   */
  /**
   * Sets the url for this Entry.

   *
   * Implementations should Touch LastModifiedTime when this method is called.

   * @param url the value to set
   */
  var url: String?

  var title: String?

  var notes: String?

  var icon: Icon?

  val lastAccessTime: Date

  val creationTime: Date

  val expiryTime: Date

  val lastModificationTime: Date

  companion object {

    /**
     * Standard properties are attributes of Entries that are accessible either by
     * dedicated methods, such as getPassword, or by [.getProperty]
     */

    val STANDARD_PROPERTY_NAME_USER_NAME = "UserName"
    val STANDARD_PROPERTY_NAME_PASSWORD = "Password"
    val STANDARD_PROPERTY_NAME_URL = "URL"
    val STANDARD_PROPERTY_NAME_TITLE = "Title"
    val STANDARD_PROPERTY_NAME_NOTES = "Notes"

    val STANDARD_PROPERTY_NAMES = Collections.unmodifiableList(Arrays.asList(
        STANDARD_PROPERTY_NAME_USER_NAME,
        STANDARD_PROPERTY_NAME_PASSWORD,
        STANDARD_PROPERTY_NAME_URL,
        STANDARD_PROPERTY_NAME_TITLE,
        STANDARD_PROPERTY_NAME_NOTES))
  }
}

abstract class AbstractEntry : Entry {
  override val path: String
    get() {
      val parent = this.parent
      var result = ""
      if (parent != null) {
        result = parent.path
      }
      return result + title
    }

  override fun toString() = this.path
}