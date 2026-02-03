
import java.util.ArrayList;
import java.util.List;

class Data {
    final List<Datum> datums;

    Data(List<Datum> datums) {
        this.datums = datums;
    }

    static class Datum { }
}

class DataUser {
    public List<Data.Datum> fi<caret>lter(Data data) {
        List<Data.Datum> datums = new ArrayList<>();
        for (Data.Datum datum : data.datums) {
            if (datum != null) datums.add(datum);
        }
        return datums;
    }
}