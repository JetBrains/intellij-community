abstract class Child implements Human {}
interface Human extends Mother, Father {
  Human me();
}

interface Father {
  Father me();
}

interface Mother {
  Mother me();
}
