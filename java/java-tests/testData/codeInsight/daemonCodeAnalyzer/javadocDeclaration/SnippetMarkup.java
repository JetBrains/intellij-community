/**
 * {@snippet :
 * System.out.println("Hello"); // @replace replacement="xyz" <warning descr="@replace: either regex or substring should be specified but not both">regex="hello"</warning> substring=oops <warning descr="@replace: duplicate attribute: 'substring'">substring=oops2</warning>   <warning descr="Markup tag or attribute expected">@hello</warning>:
 * // @replace substring="xyz" <warning descr="@replace: either regex or substring should be specified but not both">regex="oops"</warning> replacement="hello"
 * // @highlight <warning descr="@highlight: unknown type 'underlined'; only 'bold', 'italic', and 'highlighted' are supported">type=underlined</warning>
 * // <warning descr="@link: missing 'target' attribute">@link</warning>
 * }
 */
class A {
}