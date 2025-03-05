import module my.source.moduleB;
import module my.source.moduleA;
class AmbiguousModuleImport {
  <error descr="Reference to 'Imported' is ambiguous, both 'my.source.moduleB.Imported' and 'my.source.moduleA.Imported' match">Imported</error> <caret>module;
}
