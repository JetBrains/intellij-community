package plugin;

import library.JavaClass;
import library.JavaInterface;
import library.JavaMethodOwner;
import library.JavaNestedClassOwner;
import library.JavaNonExtendableNestedOwner;

import library.KotlinClass;
import library.KotlinInterface;
import library.KotlinMethodOwner;
import library.KotlinNestedClassOwner;
import library.KotlinNonExtendableNestedOwner;

//Extensions of Java classes

class JavaInheritor extends <warning descr="Class 'library.JavaClass' must not be extended">JavaClass</warning> {
}

class JavaImplementor implements <warning descr="Interface 'library.JavaInterface' must not be implemented">JavaInterface</warning> {
}

interface JavaInterfaceInheritor extends <warning descr="Interface 'library.JavaInterface' must not be extended">JavaInterface</warning> {
}

class JavaNonExtendableNestedInheritor extends JavaNonExtendableNestedOwner.<warning descr="Class 'library.JavaNonExtendableNestedOwner.NonExtendableNested' must not be extended">NonExtendableNested</warning> {
}

class JavaMethodOverrider extends JavaMethodOwner {
  @Override
  public void <warning descr="Method 'doNotOverride()' must not be overridden">doNotOverride</warning>() {
  }
}

class JavaNestedClassInheritor extends JavaNestedClassOwner.NestedClass {
}

//Extensions of Kotlin classes

class KotlinInheritor extends <warning descr="Class 'library.KotlinClass' must not be extended">KotlinClass</warning> {
}


class KotlinImplementor implements <warning descr="Interface 'library.KotlinInterface' must not be implemented">KotlinInterface</warning> {
}

interface KotlinInterfaceInheritor extends <warning descr="Interface 'library.JavaInterface' must not be extended">JavaInterface</warning> {
}

class KotlinNonExtendableNestedInheritor extends KotlinNonExtendableNestedOwner.<warning descr="Class 'library.KotlinNonExtendableNestedOwner.NonExtendableNested' must not be extended">NonExtendableNested</warning> {
}

class KotlinMethodOverrider extends KotlinMethodOwner {
  @Override
  public void <warning descr="Method 'doNotOverride()' must not be overridden">doNotOverride</warning>() {
  }
}

class KotlinNestedClassInheritor extends KotlinNestedClassOwner.NestedClass {
}


class AnynymousClasses {
  public void anonymous() {
    new <warning descr="Class 'library.JavaClass' must not be extended">JavaClass</warning>() {
    };

    new <warning descr="Interface 'library.JavaInterface' must not be implemented">JavaInterface</warning>() {
    };

    new JavaNonExtendableNestedOwner.<warning descr="Class 'library.JavaNonExtendableNestedOwner.NonExtendableNested' must not be extended">NonExtendableNested</warning>() {
    };

    new <warning descr="Class 'library.KotlinClass' must not be extended">KotlinClass</warning>() {
    };

    new <warning descr="Interface 'library.KotlinInterface' must not be implemented">KotlinInterface</warning>() {
    };

    new KotlinNonExtendableNestedOwner.<warning descr="Class 'library.KotlinNonExtendableNestedOwner.NonExtendableNested' must not be extended">NonExtendableNested</warning>() {
    };

    //No warnings
    new JavaNestedClassOwner.NestedClass() {
    };

    new KotlinNestedClassOwner.NestedClass() {
    };

    new JavaMethodOwner() {
      @Override
      public void <warning descr="Method 'doNotOverride()' must not be overridden">doNotOverride</warning>() {
      }
    };

    new KotlinMethodOwner() {
      @Override
      public void <warning descr="Method 'doNotOverride()' must not be overridden">doNotOverride</warning>() {
      }
    };
  }
}