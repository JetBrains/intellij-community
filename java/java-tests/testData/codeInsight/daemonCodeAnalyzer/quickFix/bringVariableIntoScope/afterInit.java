// "Bring 'String con' into Scope" "true"
class a {
    private void ffff(String c) {
        String con = null;
        try {
            con = null;
        } finally {
            ffff(con); // This method doesn't mind con == null.
        }
    }
}