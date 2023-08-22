import java.lang.reflect.Field;

class Reflection {
  void reflectionAccess(BeanWithWeirdFields someBean) throws NoSuchFieldException {
    Field theField = someBean.getClass().getDeclaredField("UUID");
  }

  void testOther() {
    BeanWithWeirdFields someBean = new BeanWithWeirdFields();
    System.out.println(someBean.UUID);
  }

  static class BeanWithWeirdFields {
    private String UUID;

    public void setUUID(String UUID) {
      this.UUID = UUID;
    }
  }
}