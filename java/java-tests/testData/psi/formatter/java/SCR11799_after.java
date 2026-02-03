package br.com.vivo.torpedeiro.pull.impl;

import br.com.vivo.torpedeiro.pull.MensagemPull;
import br.com.vivo.torpedeiro.pull.PullDAO;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Implementa? padr?de {@link PullDAO} que realiza todas as opera?s utilizando SQL atrav?de um {@link
 * JdbcTemplate} do Spring Framework.
 *
 * @author Marcus Brito
 */
public class PullDAOJdbcTemplate implements PullDAO
{
    private static final int STATUS_INICIAL = -7;

    private JdbcTemplate template;

    public String buscarURLProvedor(String provedor)
    {
        String sql = "SELECT url FROM pull_url WHERE provedor = ?";
        return (String) template.queryForObject(sql, new Object[]{provedor}, String.class);
    }

    public Long inserirMensagemHistorico(MensagemPull mensagem)
    {
        String sql = "" +
            "INSERT INTO pull_historico (\n" +
            "    id_log,\n" +
            "    provedor,\n" +
            "    origem,\n" +
            "    destino,\n" +
            "    msg_originada,\n" +
            "    cod_erro,\n" +
            "    dt_recepcao\n" +
            ") VALUES (?, ?, ?, ?, ?, ?, sysdate)";

        Long id = buscarProximoID();

        template.update(sql, new Object[]{id, mensagem.getCodigoProvedor(),
            mensagem.getMensagemOriginal().getOrigem(), mensagem.getMensagemOriginal().getDestino(),
            mensagem.getMensagemOriginal().getMensagem(), STATUS_INICIAL});

        return id;
    }

    public void atualizarMensagemHistorico(Long id, int status, String mensagem)
    {
        String sql = "" +
            "UPDATE pull_historico\n" +
            "   SET cod_erro     = ?,\n" +
            "       msg_recebida = ?,\n" +
            "       dt_resposta  = SYSDATE\n" +
            " WHERE id_log       = ?";

        template.update(sql, new Object[]{status, mensagem, id});
    }

    private long buscarProximoID()
    {
        return template.queryForLong("SELECT seq_id_log.nextval FROM dual");
    }

    /**
     * @param ds O DataSource utilizado para acesso ao banco de dados.
     */
    public void setDataSource(DataSource ds)
    {
        this.template = new JdbcTemplate(ds);
    }
}