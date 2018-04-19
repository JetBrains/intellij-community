typedef void (*execute)(void);
typedef id (*createItem)(const char * uid);

typedef struct ScrubberItemData {
    char * text;
    char * raster4ByteRGBA;
    int rasterW;
    int rasterH;
    execute action;
} ScrubberItemData;
