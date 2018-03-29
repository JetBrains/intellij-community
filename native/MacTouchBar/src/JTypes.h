typedef void (*execute)(void);
typedef void (*executeAt)(int index);
typedef id (*createItem)(const char * uid);

typedef struct ScrubberItemData {
    char * text;
    char * raster4ByteRGBA;
    int rasterW;
    int rasterH;
} ScrubberItemData;

typedef int (*requestScrubberItem)(int index, ScrubberItemData * out);
typedef int (*getScrubberItemsCount)(void);
