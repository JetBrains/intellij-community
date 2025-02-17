import java.util.Random;

module my.module {
  requires java.base;

  provides Random with A;
}