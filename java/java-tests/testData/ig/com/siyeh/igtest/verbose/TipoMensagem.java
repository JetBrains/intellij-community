package com.siyeh.igtest.verbose;

/**
 * Enumeração com os possíveis valores para a propriedade "tipoMensagem" de um bilhete.
 *
 * @author Marcus Brito
 */
public enum TipoMensagem
{
    MO("O"), MT("T");

    private String dbValue;

    TipoMensagem(String dbValue)
    {
        this.dbValue = dbValue;
    }

    public String getDbValue()
    {
        return dbValue;
    }

    public static TipoMensagem fromDbValue(String value)
    {
        if (value == null)
            return null;

        switch (value.charAt(0))
        {
            case 'O':
                return MO;
            case 'T':
                return MT;
            default:
                return null;
        }
    }
}
