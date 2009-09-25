import org.jetbrains.annotations.NotNull;

class B {
     @NotNull
     B b;

    public B getB() {
        return b;
    }

    public void setB(B b) {
        this.b = b;
    }

        @NotNull
        private String bug = "true";

        public boolean getBug() {
            return Boolean.valueOf(bug);
        }
}