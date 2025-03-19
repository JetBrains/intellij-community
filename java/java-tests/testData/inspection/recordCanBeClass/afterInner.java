// "Convert record to class" "true-preview"
class X {
    private static final class R {
        private R() {
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this || obj != null && obj.getClass() == this.getClass();
        }

        @Override
        public int hashCode() {
            return 1;
        }

        @Override
        public String toString() {
            return "R[]";
        }
    }
}