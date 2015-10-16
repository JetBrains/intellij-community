package cms.si.payment;

import cms.AmountMoreThanBalanceException;

import cms.db.*;

import cms.db.payments.px.PayConRequest;

import cms.db.payments.px.PayConResponse;

import cms.db.payments.px.PayConStatus;

import cms.hibernate.HibernateSessionFactory;

import cms.si.payment.px.PayConPaymentSystem;

import cms.si.payment.px.PaymentException;

import org.apache.commons.logging.Log;

import org.apache.commons.logging.LogFactory;

import org.hibernate.Query;

import org.hibernate.Session;

import javax.servlet.http.HttpServletRequest;

import java.util.*;

/**
 * @author alf
 * @author Alexey Malkovskiy
 */
public class PaymentManager {

    private static final Log log = LogFactory.getLog(PaymentManager.class);

    public static final int TRANSACTION_PENDING = 1;

    public static final int TRANSACTION_PROCESSED = 2;

    public static final int TRANSACTION_DECLINED = 3;


    public BillingInfo billCreditCard(Player player, Long transactionID, CreditCard card,

                                      double amount, String ipAddress, String customerRef) throws WrongAmountException, PaymentException {

        player.setUpdateDate(new Date());

        PayConPaymentSystem px = PayConPaymentSystem.getInstance();

        String strTransactionID = px.getTrackID(transactionID);

        // here comes authorization code
        PayConRequest request = px.createAuthRequest(player.getCurrency().getId(),

                strTransactionID, card, amount, ipAddress, player.getUsername());

        PayConResponse response = px.sendRequest(request);

        if (response.getReturnCode() != ReturnCode.RETURN_CODE_SUCCESS ||

                response.getStatus().getAuthCode() != AuthorizationFailureObject.SUCCESS)

            throw new PaymentException();

        double paid = response.getStatus().getAmountPaid();

        if (amount != paid)

            throw new WrongAmountException(amount, paid);

        player.incrementBalance(paid);

        log.info("billCreditCard player " + player.getId() + " TrackID " + strTransactionID

                + " added " + amount

                + " to balance because the credit card was charged for this amount");

        return new BillingInfo(response.getStatus().getAmountPaid(),

                response.getStatus().getAmountReq(),

                response.getStatus().getTransactionId());

    }


    public void performDeposit(double amount, Player player, CreditCardInfo info,

                               HttpServletRequest request, String remoteHost) throws PaymentException, WrongAmountException {

        org.hibernate.Transaction tx = HibernateSessionFactory.currentSession().beginTransaction();

        Transaction t = new Transaction();

        t.setAmount(amount);

        t.setType(TransactionType.DEPOSIT);

        t.setStatus(TransactionStatus.PENDING);

        t.setCreateDate(new Date());

        t.setPlayer(player);

        saveNow(t);

        try {
            BillingInfo billingInfo = billCreditCard(player, t.getId(), info, amount, remoteHost,

                    player.getUsername());

            t.setStatus(TransactionStatus.COMPLETED);

            t.setExternalId(billingInfo.getTransactionId());

            t.setProcessDate(new Date());

        } catch (PaymentException e) {
            log.error("Payment problem", e);

            // TODO
        } catch (WrongAmountException e) {
            log.error("Wrong amount", e);
            // TODO
        }

        tx.commit

                ();
    }


    private void saveNow(Transaction t) {
        Session session = HibernateSessionFactory.currentSession();

        session.
                save(t);
        session.
                flush();
    }

    @SuppressWarnings(
            "unchecked")
    public UserCashOut performCashOut(double amount, Player player) throws AmountMoreThanBalanceException {
        Session ses = HibernateSessionFactory.
                currentSession();

        if (player.getBalanceAmt() < amount) {
            throw new AmountMoreThanBalanceException();
        }

        org.
                hibernate.
                Transaction tx = ses.
                beginTransaction();

        UserCashOut userCashOut = new UserCashOut(

        );
        userCashOut.setAmount(amount);
        userCashOut.setCreateDate(new Date());
        userCashOut.setPlayer(player);
        player.getCashOuts().add(userCashOut);
        ses.save(userCashOut);
        double checkAmount = 0;
        double amountForPay = amount;
        boolean freeDebitsExist = true;
        //
        while (amountForPay > 0 && freeDebitsExist) {
            List<CashOutDebitInfo> freeDebits = findFreeDebits(player, ses);
            if (freeDebits.isEmpty()) {
                freeDebitsExist = false;
                checkAmount += amountForPay;
            } else {
                double oldAmount = amountForPay;
                amountForPay = createCredits(amountForPay, player, freeDebits, userCashOut, ses);
                amountForPay += payCredits(freeDebits, userCashOut, ses);
                if (amountForPay == oldAmount) {
                    //This is strange and hypothetical situation. It can mean
                    //that PaymentConnexions doesn't work properly. Pay by check.
                    freeDebitsExist = false;
                    checkAmount += amountForPay;
                }
            }
            ses.flush();
        }
        //
        if (checkAmount > 0) {
            payCheck(player, ses, userCashOut, checkAmount);
        }
        player.setBalanceAmt(player.getBalanceAmt() - amount);
        tx.commit();
        return userCashOut;
    }

    private void payCheck(Player player, Session ses, UserCashOut userCashOut, double checkAmount) {
        Check check = new Check();
        check.setAmount(checkAmount);
        check.setCreateDate(new Date());
        check.setPlayer(player);
        player.getChecks().add(check);
        check.setUserCashOut(userCashOut);
        userCashOut.setCheck(check);
        Transaction transaction = new Transaction();
        transaction.setAmount(-checkAmount);
        transaction.setCreateDate(new Date());
        transaction.setProcessDate(new Date());
        transaction.setType(TransactionType.CASHOUT);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setPlayer(player);
        check.setTransaction(transaction);
        transaction.setCheck(check);
        ses.save(transaction);
        ses.save(check);
    }

    private double payCredits(List<CashOutDebitInfo> freeDebits, UserCashOut userCashOut, Session ses) {
        double unpaidAmount = 0;
        for (CashOutDebitInfo info : freeDebits) {
            try {
                String trackId = "Tr" + info.credit.getId();
                PayConRequest creditRequest = PayConPaymentSystem.getInstance().createReversalRequest(userCashOut.getPlayer().getCurrency().getId(), trackId, -info.credit.getAmount(), new Long(info.debit.getExternalId()));
                PayConResponse response = PayConPaymentSystem.getInstance().sendRequest(creditRequest);
                final PayConStatus status = response.getStatus();
                if (response.getReturnCode() == ReturnCode.RETURN_CODE_SUCCESS && status.getAuthCode() == AuthorizationFailureObject.SUCCESS) {
                    info.credit.setAmount(-status.getAmountPaid());
                    info.credit.setExternalId(status.getTransactionId());
                    info.credit.setStatus(TransactionStatus.COMPLETED);
                    info.credit.setProcessDate(new Date());
                } else {
                    switch (status.getAuthCode()) {
                        case TRANSACTION_PENDING:
                            info.credit.setStatus(TransactionStatus.PENDING);
                            break;
                        case TRANSACTION_PROCESSED:
                            //This is hypothetical situation. We have to delete this credit
                            //transaction. Original debit transaction still free for
                            //future credits.
                            ses.delete(info.credit);
                            info.credit = null;
                            break;
                        case TRANSACTION_DECLINED:
                        default:
                            info.debit.getCreditTransactions().remove(info.credit);
                            info.credit.setOriginalDebitTransaction(null);
                            info.debit.setCreditStatus("ERROR - " + status.getAuthCode());
                            userCashOut.getCredits().remove(info.credit);
                            info.credit.setUserCashOut(null);
                            unpaidAmount += -info.credit.getAmount();
                            ses.delete(info.credit);
                            break;
                    }
                }
            } catch (PaymentException e) {
                // ignore
                //log.error(e.getMessage(), e);
            }
        }
        return unpaidAmount;
    }

    private double createCredits(double amount, Player player, List<CashOutDebitInfo> freeDebits, UserCashOut userCashOut, Session ses) {
        double checkAmount = amount;
        int i = 0;
        for (CashOutDebitInfo debitInfo : freeDebits) {
            Transaction credit = new Transaction();
            if (checkAmount > debitInfo.remainder) {
                credit.setAmount(-debitInfo.remainder);
                checkAmount -= debitInfo.remainder;
            } else {
                credit.setAmount(-checkAmount);
                checkAmount = 0;
            }
            credit.setCreateDate(new Date());
            credit.setOriginalDebitTransaction(debitInfo.debit);
            debitInfo.debit.getCreditTransactions().add(credit);
            credit.setPlayer(player);
            credit.setType(TransactionType.CASHOUT);
            credit.setStatus(TransactionStatus.PENDING);
            credit.setUserCashOut(userCashOut);
            userCashOut.getCredits().add(credit);
            debitInfo.credit = credit;
            ses.save(credit);
            i++;
            if (checkAmount <= 0) {
                break;
            }
        }
        int size = freeDebits.size();
        for (int j = i; j < size; j++) {
            freeDebits.remove(i);
        }
        return checkAmount;
    }

    @SuppressWarnings("unchecked")
    private List<CashOutDebitInfo> findFreeDebits(Player player, Session ses) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -6);
        Query query = ses.createQuery("from Transaction as t where " + "t.player = :player and t.createDate > :date " + "and t.creditStatus is null and t.type = :type and " + "(t.amount > (select sum(-t2.amount) from t.creditTransactions as t2) " + "or t.creditTransactions is empty) "
                //+ "where t2.status = :status) or t.creditTransactions is empty) "
                + "and t.status = :status " + "and t.externalId is not null " + "order by t.createDate");
        query.setEntity("player", player);
        query.setDate("date", calendar.getTime());
        query.setInteger("type", TransactionType.DEPOSIT.getId());
        query.setInteger("status", TransactionStatus.COMPLETED.getId());
        //query.setInteger("status", TransactionStatus.COMPLETED.getId());
        List<Transaction> transactions = query.list();
        List<CashOutDebitInfo> freeDebits = new ArrayList<CashOutDebitInfo>();
        for (Transaction transaction : transactions) {
            Set<Transaction> credits = transaction.getCreditTransactions();
            double sum = 0;
            for (Transaction credit : credits) {
                sum += credit.getAmount();
            }
            CashOutDebitInfo info = new CashOutDebitInfo();
            info.debit = transaction;
            info.remainder = transaction.getAmount() - sum;
            freeDebits.add(info);
        }
        return freeDebits;
    }
}