import module java<caret>.base;

module my.module {
  requires java.base;

  provides Random with A;
}