class Entity {}
class EntityBuilder {}

class Tester {
  private Entity entity;

  private void build(EntityBuilder builder) {
  }

  public void test1() {
      bui<caret>new EntityBuilder();
  }
}
