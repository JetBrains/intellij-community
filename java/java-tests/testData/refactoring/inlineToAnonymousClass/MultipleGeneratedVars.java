class PrivateInitUser {
        public void method() {
          new WithPrivateInit(new CustomType("a"));
          new WithPrivateInit(new CustomType("b"));
        }

        public static class CustomType {
                public CustomType(String s) {
                }
        }
}

class <caret>WithPrivateInit {
        public WithPrivateInit(PrivateInitUser.CustomType customType) {
                privateMethod(customType);
        }

        private void privateMethod(PrivateInitUser.CustomType customType) {
        }
}
