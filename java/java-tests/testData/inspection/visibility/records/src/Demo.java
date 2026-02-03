class Demo {
    public static void main(String... args) {
        User user = new User(null, null);
        System.out.println(user.email());
        user.xylophone();
    }

    public record User(String email, String phone){
        public User {
            if (email == null && phone == null) {
                throw new IllegalArgumentException();
            }
        }

        public String email() {
            return email;
        }

        /*Access can be 'private'*/public/**/ void xylophone() {

        }
    }
}