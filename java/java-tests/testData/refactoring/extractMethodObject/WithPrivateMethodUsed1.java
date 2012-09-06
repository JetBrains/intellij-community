import java.util.Date;

public class TestClass {

    private Date expiry;
    private Date maturity;
    private int commitment;
    private double outstanding;
    private Iterable payments;
    private Date today;
    private Date start;
    private int riskRating;


    public double cal<caret>culate() {
        return ( outstandingRiskAmount() * duration() * riskFactor() ) + ( unusedRiskAmount() * duration() * unusedRiskFactor() );
    }

    private double riskFactor() {
        return 5.0;
    }

    private double unusedRiskFactor() {
        return 6.0;
    }

    private double unusedRiskAmount() {
        return ( commitment - outstanding );
    }

    private double outstandingRiskAmount() {
        return outstanding;
    }

    private double getUsedPercentage() {
        return 1.0;
    }

    private double duration() {
        if ( expiry == null && maturity != null ) {
            return 1.0;
        } else if ( expiry != null && maturity == null ) {
            return yearsTo( expiry );
        }
        return 0.0;
    }

    private double yearsTo( final Date endDate ) {
        Date beginDate = ( today == null ? start : today );
        return ( ( endDate.getTime() - beginDate.getTime() ));
    }
}