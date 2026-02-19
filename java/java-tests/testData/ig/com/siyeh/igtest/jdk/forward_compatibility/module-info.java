module M {
  requires <warning descr="Modifiers on 'requires java.base' are prohibited in releases since Java 10">static</warning> <warning descr="Modifiers on 'requires java.base' are prohibited in releases since Java 10">transitive</warning> <error descr="Module not found: java.base">java.base</error>;
}