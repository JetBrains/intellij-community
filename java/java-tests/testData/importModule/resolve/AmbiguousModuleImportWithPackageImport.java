import module my.source.moduleB;

import my.source.moduleA.*;

class AmbiguousModuleImportWithPackageImport {
  <error descr="Reference to 'Imported' is ambiguous, both 'my.source.moduleA.Imported' and 'my.source.moduleB.Imported' match">Imported</error> module;
}
