/*******************************************************************************
 * Copyright [yyyy] [name of copyright owner]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package x;
/**
 * abc <warning descr="Link specified as plain text">http://vk.com</warning> def
 */
class A { }

/**
 * @see https://www.wikipedia.org/ Wikipedia
 */
class B { }

/**
 * <pre>{@code
 * // https://www.wikipedia.org/
 * }</pre>
 */
class C { }

/**
 * abc <a href="http://www.wikipedia.org/">https://www.wikipedia.org/</a> def
 */
class D {
}

/**
 * abc <a
 * href
 * =
 * "
 * https://www.wikipedia.org/
 * "
 * >
 * --->
 * https://www.wikipedia.org/
 * <---
 * </a> def
 */
class E {
}

/**
 * <img alt="Flag of Italy" src="https://upload.wikimedia.org/wikipedia/en/thumb/0/03/Flag_of_Italy.svg/510px-Flag_of_Italy.svg.png">
 */
class F {}

/**
 * <img
 * src
 * =
 * "
 * https://upload.wikimedia.org/wikipedia/en/thumb/0/03/Flag_of_Italy.svg/510px-Flag_of_Italy.svg.png
 * "
 * alt="Flag of Italy"
 * >
 */
class G {}
