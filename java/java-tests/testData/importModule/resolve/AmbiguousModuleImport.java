import module my.source.moduleB;
import module my.source.moduleA;
class AmbiguousModuleImport {
  <error descr="Reference to 'Imported' is ambiguous, both 'my.source.moduleA.Imported' and 'my.source.moduleB.Imported' match">Imported</error> <caret>module;
}
