class S extends S3 {
    String XXX;  //should correctly resolve to java.lang.String
}

class S3 {
    private class String {
    }
}
