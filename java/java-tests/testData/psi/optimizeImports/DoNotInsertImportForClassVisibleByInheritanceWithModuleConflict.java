import module java.base;

import one.Super;
import two.*;
import three.*;

class DoNotInsertImportForClassVisibleByInheritanceWithModuleConflict implements Super {
  One one;
  Two two;
  Three three;
  Four four;
  Five five;
  Six six;
  Seven seven;
  Eight eight;
  Nine nine;
  Ten ten;

  Result x() {
    return new Result();
  }
}